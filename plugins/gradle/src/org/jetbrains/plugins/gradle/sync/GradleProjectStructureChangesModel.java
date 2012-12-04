package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.diff.GradleChangesCalculationContext;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleStructureChangesCalculator;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.model.gradle.GradleProject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages information about the changes between the gradle and intellij project structures. 
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 7:01 PM
 */
public class GradleProjectStructureChangesModel extends AbstractProjectComponent {

  private final Set<GradleProjectStructureChangeListener> myListeners = new CopyOnWriteArraySet<GradleProjectStructureChangeListener>();
  private final AtomicReference<Set<GradleProjectStructureChange>> myChanges
    = new AtomicReference<Set<GradleProjectStructureChange>>(new HashSet<GradleProjectStructureChange>());
  
  private final AtomicReference<GradleProject> myGradleProject = new AtomicReference<GradleProject>();

  @NotNull private final GradleStructureChangesCalculator<GradleProject, Project> myChangesCalculator;
  @NotNull private final PlatformFacade myPlatformFacade;

  public GradleProjectStructureChangesModel(@NotNull Project project,
                                            @NotNull GradleStructureChangesCalculator<GradleProject, Project> changesCalculator,
                                            @NotNull PlatformFacade platformFacade)
  {
    super(project);
    myChangesCalculator = changesCalculator;
    myPlatformFacade = platformFacade;
  }

  /**
   * Asks the model to update its state according to the given state of the target gradle project.
   * <p/>
   * I.e. basically the processing looks as following:
   * <ol>
   *  <li>This method is called;</li>
   *  <li>
   *    The model process given project state within the {@link #getChanges() registered changes} and calculates resulting difference
   *    between Gradle and IJ projects;
   *  </li>
   *  <li>{@link #addListener(GradleProjectStructureChangeListener) Registered listeners} are notified if any new change is detected;</li>
   * </ol>
   * <p/>
   * <b>Note:</b> it's very important that the listeners are notified <b>after</b> the actual state change, i.e. {@link #getChanges()}
   * during the update returns up-to-date data.
   *
   * @param gradleProject  gradle project to sync with
   */
  public void update(@NotNull GradleProject gradleProject) {
    myGradleProject.set(gradleProject);
    GradleChangesCalculationContext context = new GradleChangesCalculationContext(myChanges.get(), myPlatformFacade);
    myChangesCalculator.calculate(gradleProject, myProject, context);
    if (!context.hasNewChanges()) {
      return;
    }
    myChanges.set(context.getCurrentChanges());
    for (GradleProjectStructureChangeListener listener : myListeners) {
      listener.onChanges(context.getKnownChanges(), context.getCurrentChanges());
    }
  }

  /**
   * @return    last known gradle project state
   */
  @Nullable
  public GradleProject getGradleProject() {
    return myGradleProject.get();
  }
  
  /**
   * Registers given listener within the current model.
   * 
   * @param listener  listener to register
   * @return          <code>true</code> if given listener was not registered before;
   *                  <code>false</code> otherwise
   */
  public boolean addListener(@NotNull GradleProjectStructureChangeListener listener) {
    return myListeners.add(listener);
  }

  /**
   * @return collection of project structure changes registered within the current model
   */
  @NotNull
  public Set<GradleProjectStructureChange> getChanges() {
    return myChanges.get();
  }

  public void clearChanges() {
    myChanges.get().clear();
  }
}
