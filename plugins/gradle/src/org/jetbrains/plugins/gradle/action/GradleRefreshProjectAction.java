package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.gradle.config.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.io.File;

/**
 * Forces the 'gradle' plugin to retrieve the most up-to-date info about the
 * {@link GradleSettings#LINKED_PROJECT_FILE_PATH linked gradle project} and update all affected control if necessary
 * (like project structure UI, tasks list etc).
 * 
 * @author Denis Zhdanov
 * @since 1/23/12 3:48 PM
 */
public class GradleRefreshProjectAction extends AnAction implements DumbAware {

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
    e.getPresentation().setVisible(path != null && new File(path).isFile());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    // TODO den implement 
    int i = 1;
  }
}
