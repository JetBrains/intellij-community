package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;

import java.io.File;

/**
 * Encapsulates functionality of patching problems from the previous gradle integration releases.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/19/12 3:48 PM
 */
public class GradlePatcher {

  @SuppressWarnings("MethodMayBeStatic")
  public void patch(@NotNull Project project) {
    patchGradleHomeIfNecessary(project);
  }

  private static void patchGradleHomeIfNecessary(@NotNull Project project) {
    // Old gradle integration didn't save gradle home at project-local settings (only default project has that information).
    
    final Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    if (defaultProject.equals(project)) {
      return;
    }

    // Propagate gradle settings from the current project to the default project if necessary.
    // TODO den implement
//    final GradleSettings defaultProjectSettings = GradleSettings.getInstance(defaultProject);
//    final GradleSettings currentProjectSettings = GradleSettings.getInstance(project);
//    String projectGradleHome = currentProjectSettings.getGradleHome();
//    String defaultGradleHome = defaultProjectSettings.getGradleHome();
//    if (StringUtil.isEmpty(projectGradleHome) || !StringUtil.isEmpty(defaultGradleHome)) {
//      return;
//    }
//    GradleInstallationManager libraryManager = ServiceManager.getService(GradleInstallationManager.class);
//    File autodetectedGradleHome = libraryManager.getAutodetectedGradleHome();
//    // We don't want to store auto-detected value at the settings.
//    if (autodetectedGradleHome == null || !FileUtil.filesEqual(autodetectedGradleHome, new File(projectGradleHome))) {
//      GradleSettings.applyGradleHome(projectGradleHome, defaultProject);
//    }
  }
}
