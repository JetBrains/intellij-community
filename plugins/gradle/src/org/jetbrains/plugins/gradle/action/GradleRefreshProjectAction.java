package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleSettings;
import org.jetbrains.plugins.gradle.notification.GradleConfigNotificationManager;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureTreeModel;
import org.jetbrains.plugins.gradle.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.task.GradleTaskType;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * Forces the 'gradle' plugin to retrieve the most up-to-date info about the
 * {@link GradleSettings#getLinkedProjectPath() linked gradle project} and update all affected control if necessary
 * (like project structure UI, tasks list etc).
 * 
 * @author Denis Zhdanov
 * @since 1/23/12 3:48 PM
 */
public class GradleRefreshProjectAction extends AbstractGradleLinkedProjectAction implements DumbAware, AnAction.TransparentUpdate {

  private final Ref<String> myErrorMessage = new Ref<String>();

  public GradleRefreshProjectAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.refresh.project.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.refresh.project.description"));
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent event, @NotNull Project project, @NotNull String linkedProjectPath) {
    String message = myErrorMessage.get();
    if (message != null) {
      GradleProjectStructureTreeModel model = GradleUtil.getProjectStructureTreeModel(event.getDataContext());
      if (model != null) {
        model.rebuild();
      }
      GradleConfigNotificationManager notificationManager = project.getComponent(GradleConfigNotificationManager.class);
      notificationManager.processRefreshError(message);
      myErrorMessage.set(null);
    }
    boolean enabled = false;
    final GradleTaskManager taskManager = project.getComponent(GradleTaskManager.class);
    if (taskManager != null) {
      enabled = !taskManager.hasTaskOfTypeInProgress(GradleTaskType.RESOLVE_PROJECT);
    }
    event.getPresentation().setEnabled(enabled);
  }

  @Override
  protected void doActionPerformed(@NotNull final Project project, @NotNull final String linkedProjectPath) {
    // We save all documents because there is more than one target 'build.gradle' file in case of multi-module gradle project.
    FileDocumentManager.getInstance().saveAllDocuments();

    GradleConfigNotificationManager notificationManager = project.getComponent(GradleConfigNotificationManager.class);
    if (!GradleUtil.isGradleAvailable(project)) {
      notificationManager.processUnknownGradleHome();
      return;
    }

    myErrorMessage.set(null);
    GradleUtil.refreshProject(project, myErrorMessage);
  }
}
