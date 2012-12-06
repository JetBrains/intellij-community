package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.gradle.config.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * Allows to link gradle project to the current IntelliJ IDEA project.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 12/26/11 5:09 PM
 */
public class GradleLinkToProjectAction extends AnAction implements DumbAware {

  public GradleLinkToProjectAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.link.project.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.link.project.description"));
    getTemplatePresentation().setText(GradleBundle.message("gradle.toolwindow.linked.action.text"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }
    final VirtualFile file = FileChooser.chooseFile(GradleUtil.getGradleProjectFileChooserDescriptor(), project, null);
    if (file == null) {
      return;
    }
    GradleSettings.applyLinkedProjectPath(file.getPath(), project);
  }
}
