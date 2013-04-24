package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import com.intellij.openapi.externalSystem.service.task.ExternalSystemTaskManager;
import org.jetbrains.plugins.gradle.notification.GradleConfigNotificationManager;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * Forces the 'gradle' plugin to retrieve the most up-to-date info about the
 * {@link GradleSettings#getLinkedExternalProjectPath() linked gradle project} and update all affected control if necessary
 * (like project structure UI, tasks list etc).
 * 
 * @author Denis Zhdanov
 * @since 1/23/12 3:48 PM
 */
public class GradleRefreshProjectAction extends AbstractGradleLinkedProjectAction implements DumbAware, AnAction.TransparentUpdate {

  private final Ref<String> myErrorMessage = new Ref<String>();

  public GradleRefreshProjectAction() {
    // TODO den uncomment
    //getTemplatePresentation().setText(ExternalSystemBundle.message("gradle.action.refresh.project.text"));
    //getTemplatePresentation().setDescription(ExternalSystemBundle.message("gradle.action.refresh.project.description"));
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent event, @NotNull Project project, @NotNull String linkedProjectPath) {
    String message = myErrorMessage.get();
    if (message != null) {
      // TODO den implement
//      ExternalProjectStructureTreeModel model = GradleUtil.getProjectStructureTreeModel(event.getDataContext());
//      if (model != null) {
//        model.rebuild();
//      }
      GradleConfigNotificationManager notificationManager = ServiceManager.getService(project, GradleConfigNotificationManager.class);
      notificationManager.processRefreshError(message);
      myErrorMessage.set(null);
    }
    boolean enabled = false;
    final ExternalSystemTaskManager taskManager = ServiceManager.getService(project, ExternalSystemTaskManager.class);
    if (taskManager != null) {
      enabled = !taskManager.hasTaskOfTypeInProgress(ExternalSystemTaskType.RESOLVE_PROJECT);
    }
    event.getPresentation().setEnabled(enabled);
  }

  @Override
  protected void doActionPerformed(@NotNull AnActionEvent event, @NotNull final Project project, @NotNull final String linkedProjectPath) {
    // We save all documents because there is more than one target 'build.gradle' file in case of multi-module gradle project.
    FileDocumentManager.getInstance().saveAllDocuments();

    GradleConfigNotificationManager notificationManager = ServiceManager.getService(project, GradleConfigNotificationManager.class);
    if (!GradleUtil.isGradleAvailable(project)) {
      notificationManager.processUnknownGradleHome();
      return;
    }

    myErrorMessage.set(null);
    ExternalSystemUtil.refreshProject(project, GradleConstants.SYSTEM_ID, myErrorMessage);
  }
}
