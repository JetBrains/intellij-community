package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

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

  public GradleProjectStructureChangesModel(@NotNull Project project) {
    super(project);
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
}
