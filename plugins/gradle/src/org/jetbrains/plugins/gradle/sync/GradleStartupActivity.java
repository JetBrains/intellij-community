package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradlePatcher;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * Performs gradle-specific actions on IJ project loading.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/13/12 12:01 PM
 */
// TODO den generalize and move to 'external-system'
public class GradleStartupActivity implements StartupActivity {
  @SuppressWarnings("UseOfArchaicSystemPropertyAccessors")
  @Override
  public void runActivity(@NotNull final Project project) {
    Runnable task = new Runnable() {
      @Override
      public void run() {
        new GradlePatcher().patch(project);
        
        if (!Boolean.getBoolean(ExternalSystemConstants.NEWLY_IMPORTED_PROJECT)) {
          ExternalSystemUtil.refreshProject(project, GradleConstants.SYSTEM_ID);
        }
      }
    };
    
    if (project.isInitialized()) {
      task.run();
    }
    else {
      StartupManager.getInstance(project).registerPostStartupActivity(task);
    }
  }
}
