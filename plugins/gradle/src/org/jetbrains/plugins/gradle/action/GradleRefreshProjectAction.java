package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleSettings;
import org.jetbrains.plugins.gradle.task.GradleResolveProjectTask;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Forces the 'gradle' plugin to retrieve the most up-to-date info about the
 * {@link GradleSettings#LINKED_PROJECT_FILE_PATH linked gradle project} and update all affected control if necessary
 * (like project structure UI, tasks list etc).
 * 
 * @author Denis Zhdanov
 * @since 1/23/12 3:48 PM
 */
public class GradleRefreshProjectAction extends AnAction implements DumbAware {

  private final AtomicBoolean myInProgress = new AtomicBoolean();
  
  public GradleRefreshProjectAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.refresh.project.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.refresh.project.description"));
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      e.getPresentation().setVisible(false);
      return;
    }
    final String path = GradleSettings.getInstance(project).LINKED_PROJECT_FILE_PATH;
    final boolean visible = path != null && new File(path).isFile();
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(!myInProgress.get());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      e.getPresentation().setVisible(false);
      return;
    }
    // Assuming that the linked project is available if this action is called (update() is successful)
    final String projectPath = GradleSettings.getInstance(project).LINKED_PROJECT_FILE_PATH;
    myInProgress.set(true);
    ProgressManager.getInstance().run(new Task.Backgroundable(project, GradleBundle.message("gradle.sync.progress.text")) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          GradleResolveProjectTask task = new GradleResolveProjectTask(project, projectPath, true);
          task.execute(indicator);
        }
        finally {
          myInProgress.set(false);
        }
      }
    });
  }
}
