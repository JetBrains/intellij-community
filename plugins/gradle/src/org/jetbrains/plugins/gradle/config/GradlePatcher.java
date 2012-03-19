package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates functionality of patching problems from the previous gradle integration releases.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/19/12 3:48 PM
 */
public class GradlePatcher extends AbstractProjectComponent {

  public GradlePatcher(@NotNull Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    Runnable task = new Runnable() {
      @Override
      public void run() {
        patchGradleHomeIfNecessary();
      }
    };

    if (myProject.isInitialized()) {
      task.run();
    }
    else {
      StartupManager.getInstance(myProject).registerPostStartupActivity(task);
    }
  }

  private void patchGradleHomeIfNecessary() {
    // Old gradle integration didn't save gradle home at project-local settings (only default project has that information).
    
    final Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    if (defaultProject.equals(myProject)) {
      return;
    }

    final GradleSettings defaultProjectSettings = GradleSettings.getInstance(defaultProject);
    final GradleSettings currentProjectSettings = GradleSettings.getInstance(myProject);
    if (!StringUtil.isEmpty(currentProjectSettings.getGradleHome()) && StringUtil.isEmpty(defaultProjectSettings.getGradleHome())) {
      GradleSettings.applyGradleHome(currentProjectSettings.getGradleHome(), defaultProject);
    }
    else if (!StringUtil.isEmpty(defaultProjectSettings.getGradleHome())
             && StringUtil.isEmpty(currentProjectSettings.getGradleHome()))
    {
      GradleSettings.applyGradleHome(defaultProjectSettings.getGradleHome(), myProject);
    }
  }
}
