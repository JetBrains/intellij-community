package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ConcurrentHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangesCalculator;
import org.jetbrains.plugins.gradle.model.GradleProject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * // TODO den add doc
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 7:01 PM
 */
public class GradleProjectStructureChangesModel extends AbstractProjectComponent {

  private final Set<GradleProjectStructureChangeListener> myListeners = new CopyOnWriteArraySet<GradleProjectStructureChangeListener>();
  private final Set<GradleProjectStructureChange>         myChanges   = new ConcurrentHashSet<GradleProjectStructureChange>();

  private final GradleProjectStructureChangesCalculator myChangesCalculator;

  public GradleProjectStructureChangesModel(@NotNull Project project, @NotNull GradleProjectStructureChangesCalculator changesCalculator) {
    super(project);
    myChangesCalculator = changesCalculator;
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
   *
   * @param gradleProject  gradle project to sync with
   */
  public void update(@NotNull GradleProject gradleProject) {
    Set<GradleProjectStructureChange> knownChanges = new HashSet<GradleProjectStructureChange>(myChanges);
    final Set<GradleProjectStructureChange> newChanges = myChangesCalculator.calculate(gradleProject, myProject, knownChanges);
    myChanges.addAll(newChanges);
    for (GradleProjectStructureChangeListener listener : myListeners) {
      listener.onChanges(newChanges);
    }
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
    return myChanges;
  }
}
