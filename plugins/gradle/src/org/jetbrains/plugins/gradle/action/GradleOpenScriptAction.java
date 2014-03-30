package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * Forces the IntelliJ IDEA to open {@link GradleSettings#getLinkedExternalProjectPath() linked gradle project} at the editor
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since 1/31/12 5:16 PM
 */
public class GradleOpenScriptAction extends AbstractGradleLinkedProjectAction implements DumbAware {

  private static final Logger LOG = Logger.getInstance("#" + GradleOpenScriptAction.class.getName());

  public GradleOpenScriptAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.open.script.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.open.script.description"));
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent event, @NotNull Project project, @NotNull String linkedProjectPath) {
  }

  @Override
  protected void doActionPerformed(@NotNull AnActionEvent event, @NotNull Project project, @NotNull String linkedProjectPath) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(linkedProjectPath);
    if (virtualFile == null) {
      LOG.warn(String.format("Can't obtain virtual file for the target file path: '%s'", linkedProjectPath));
      return;
    }
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile);
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }
}
