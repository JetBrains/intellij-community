package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.diff.GradleChangesCalculationContext;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleStructureChangesCalculator;
import org.jetbrains.plugins.gradle.model.gradle.GradleProject;
import org.jetbrains.plugins.gradle.util.GradleLibraryPathTypeMapper;

import java.util.Collection;
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
public class GradleProjectStructureChangesModel {

  private final Set<GradleProjectStructureChangeListener>          myListeners =
    new CopyOnWriteArraySet<GradleProjectStructureChangeListener>();
  private final AtomicReference<Set<GradleProjectStructureChange>> myChanges   =
    new AtomicReference<Set<GradleProjectStructureChange>>(new HashSet<GradleProjectStructureChange>());

  private final AtomicReference<GradleProject>                         myGradleProject  = new AtomicReference<GradleProject>();
  private final Collection<GradleProjectStructureChangesPostProcessor> myPostProcessors = ContainerUtilRt.createEmptyCOWList();

  @NotNull private final GradleStructureChangesCalculator<GradleProject, Project> myChangesCalculator;
  @NotNull private final PlatformFacade                                           myPlatformFacade;
  @NotNull private final GradleLibraryPathTypeMapper                              myLibraryPathTypeMapper;
  @NotNull private final Project                                                  myProject;

  public GradleProjectStructureChangesModel(@NotNull Project project,
                                            @NotNull GradleStructureChangesCalculator<GradleProject, Project> changesCalculator,
                                            @NotNull PlatformFacade platformFacade,
                                            @NotNull GradleLibraryPathTypeMapper mapper,
                                            @NotNull GradleMovedJarsPostProcessor movedJarsPostProcessor,
                                            @NotNull GradleOutdatedLibraryVersionPostProcessor changedLibraryVersionPostProcessor)
  {
    myProject = project;
    myChangesCalculator = changesCalculator;
    myPlatformFacade = platformFacade;
    myLibraryPathTypeMapper = mapper;
    myPostProcessors.add(movedJarsPostProcessor);
    myPostProcessors.add(changedLibraryVersionPostProcessor);
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
    final GradleChangesCalculationContext context = getCurrentChangesContext(gradleProject);
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
  
  @NotNull
  @TestOnly
  public Collection<GradleProjectStructureChangesPostProcessor> getPostProcessors() {
    return myPostProcessors;
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

  @NotNull
  public GradleChangesCalculationContext getCurrentChangesContext(@NotNull GradleProject gradleProject) {
    GradleChangesCalculationContext context
      = new GradleChangesCalculationContext(myChanges.get(), myPlatformFacade, myLibraryPathTypeMapper);
    myChangesCalculator.calculate(gradleProject, myProject, context);
    for (GradleProjectStructureChangesPostProcessor processor : myPostProcessors) {
      processor.processChanges(context.getCurrentChanges(), myProject);
    }
    return context;
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
