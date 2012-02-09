package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleSettings;
import org.jetbrains.plugins.gradle.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.task.GradleTaskType;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * Forces the 'gradle' plugin to retrieve the most up-to-date info about the
 * {@link GradleSettings#LINKED_PROJECT_FILE_PATH linked gradle project} and update all affected control if necessary
 * (like project structure UI, tasks list etc).
 * 
 * @author Denis Zhdanov
 * @since 1/23/12 3:48 PM
 */
public class GradleRefreshProjectAction extends AbstractGradleLinkedProjectAction implements DumbAware, AnAction.TransparentUpdate {

  public GradleRefreshProjectAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.refresh.project.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.refresh.project.description"));
  }

  @Override
  protected void doUpdate(@NotNull Presentation presentation, @NotNull Project project, @NotNull String linkedProjectPath) {
    boolean enabled = false;
    final GradleTaskManager taskManager = ServiceManager.getService(GradleTaskManager.class);
    if (taskManager != null) {
      enabled = !taskManager.hasTaskOfTypeInProgress(GradleTaskType.RESOLVE_PROJECT);
    }
    presentation.setEnabled(enabled); 
  }

  @Override
  protected void doActionPerformed(@NotNull final Project project, @NotNull final String linkedProjectPath) {
    GradleUtil.refreshProject(project); 
  }
}
