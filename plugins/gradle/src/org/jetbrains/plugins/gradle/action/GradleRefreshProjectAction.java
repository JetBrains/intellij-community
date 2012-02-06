package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleSettings;
import org.jetbrains.plugins.gradle.task.GradleResolveProjectTask;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Forces the 'gradle' plugin to retrieve the most up-to-date info about the
 * {@link GradleSettings#LINKED_PROJECT_FILE_PATH linked gradle project} and update all affected control if necessary
 * (like project structure UI, tasks list etc).
 * 
 * @author Denis Zhdanov
 * @since 1/23/12 3:48 PM
 */
public class GradleRefreshProjectAction extends AbstractGradleLinkedProjectAction implements DumbAware {

  private final AtomicBoolean myInProgress = new AtomicBoolean();
  
  public GradleRefreshProjectAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.refresh.project.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.refresh.project.description"));
  }

  @Override
  protected void doUpdate(@NotNull Presentation presentation, @NotNull String linkedProjectPath) {
    presentation.setEnabled(!myInProgress.get()); 
  }

  @Override
  protected void doActionPerformed(@NotNull final Project project, @NotNull final String linkedProjectPath) {
    myInProgress.set(true);
    ProgressManager.getInstance().run(new Task.Backgroundable(project, GradleBundle.message("gradle.sync.progress.text")) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          GradleResolveProjectTask task = new GradleResolveProjectTask(project, linkedProjectPath, true);
          task.execute(indicator);
        }
        finally {
          myInProgress.set(false);
        }
      }
    }); 
  }
}
