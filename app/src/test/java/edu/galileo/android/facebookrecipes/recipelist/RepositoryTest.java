package edu.galileo.android.facebookrecipes.recipelist;

import com.raizlabs.android.dbflow.list.FlowCursorList;
import com.raizlabs.android.dbflow.sql.language.Select;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import edu.galileo.android.facebookrecipes.BuildConfig;
import edu.galileo.android.facebookrecipes.FacebookRecipesApp;
import edu.galileo.android.facebookrecipes.entities.Recipe;
import edu.galileo.android.facebookrecipes.entities.Recipe_Table;
import edu.galileo.android.facebookrecipes.lib.EventBus;
import edu.galileo.android.facebookrecipes.recipelist.events.RecipeListEvent;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * Created by ykro.
 */
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class RepositoryTest {
    @Mock
    private EventBus eventBus;
    private FacebookRecipesApp app;
    private RecipeListRepository repository;
    private ArgumentCaptor<RecipeListEvent> recipeListEventArgumentCaptor;

    private final static int RECIPES_IN_DELETE_EVENT = 1;

    @Before
    public void setupRepository() {
        MockitoAnnotations.initMocks(this);
        repository = new RecipeListRepositoryImpl(eventBus);
        recipeListEventArgumentCaptor = ArgumentCaptor.forClass(RecipeListEvent.class);
        app = (FacebookRecipesApp) RuntimeEnvironment.application;
        app.onCreate();
    }

    @After
    public void tearDown() throws Exception {
        app.onTerminate();
    }

    @Test
    public void getSavedRecipesCalled_eventPosted() {
        //setup
        FlowCursorList<Recipe> storedRecipes = new FlowCursorList<>(false, Recipe.class);
        int storedRecipesOriginalSize = storedRecipes.getCount();

        int recipesToStore = 5;
        Recipe currentRecipe;
        List<Recipe> testRecipeList = new ArrayList<>();
        for (int i = 0; i < recipesToStore; i++) {
            currentRecipe = new Recipe();
            currentRecipe.setRecipeId("id " + i);
            currentRecipe.save();
            testRecipeList.add(currentRecipe);
        }
        storedRecipes.refresh();

        //test
        repository.getSavedRecipes();
        verify(eventBus).post(recipeListEventArgumentCaptor.capture());

        RecipeListEvent event = recipeListEventArgumentCaptor.getValue();
        assertEquals(RecipeListEvent.READ_EVENT, event.getType());

        List<Recipe> recipesFromEvent = event.getRecipes();
        List<Recipe> recipesFromDB = storedRecipes.getAll();
        assertEquals(recipesFromEvent.size(), recipesToStore);
        assertEquals(recipesFromEvent, recipesFromDB);

        //teardown
        for (Recipe recipe : testRecipeList) {
            recipe.delete();
        }

        storedRecipes.refresh();
        //check that test's changes are removed
        assertEquals(storedRecipesOriginalSize, storedRecipes.getCount());
    }

    @Test
    public void updateRecipesCalled_eventPosted() {
        String newRecipeId = "id1";
        String titleBefore = "title before update";
        String titleAfter = "title after update";
        Recipe recipe = new Recipe();

        recipe.setRecipeId(newRecipeId);
        recipe.setTitle(titleBefore);
        recipe.save();
        recipe.setTitle(titleAfter);

        repository.updateRecipe(recipe);
        Recipe recipeFromDB = new Select()
                .from(Recipe.class)
                .where(Recipe_Table.recipeId.is(newRecipeId))
                .querySingle();
        assertTrue(recipeFromDB.getTitle().equals(titleAfter));
        verify(eventBus).post(recipeListEventArgumentCaptor.capture());

        RecipeListEvent event = recipeListEventArgumentCaptor.getValue();
        assertEquals(RecipeListEvent.UPDATE_EVENT, event.getType());
    }

    @Test
    public void removeRecipesCalled_eventPosted() {
        String newRecipeId = "id1";
        Recipe recipe = new Recipe();
        recipe.setRecipeId(newRecipeId);
        recipe.save();

        assertTrue(recipe.exists());
        repository.removeRecipe(recipe);
        verify(eventBus).post(recipeListEventArgumentCaptor.capture());
        assertFalse(recipe.exists());

        RecipeListEvent event = recipeListEventArgumentCaptor.getValue();
        assertEquals(RecipeListEvent.DELETE_EVENT, event.getType());


        List<Recipe> recipesFromEvent = event.getRecipes();
        assertEquals(recipesFromEvent.size(), RECIPES_IN_DELETE_EVENT);
        Recipe recipeToDelete = recipesFromEvent.get(0);
        assertEquals(recipeToDelete, recipe);
    }
}
