package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * Forces the IntelliJ IDEA to open {@link GradleSettings#LINKED_PROJECT_FILE_PATH linked gradle project} at the editor
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/31/12 5:16 PM
 */
public class GradleOpenScriptAction extends AbstractGradleLinkedProjectAction implements DumbAware {

  public GradleOpenScriptAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.open.script.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.open.script.description"));
  }

  @Override
  protected void doUpdate(@NotNull Presentation presentation, @NotNull String linkedProjectPath) {
  }

  @Override
  protected void doActionPerformed(@NotNull Project project, @NotNull String linkedProjectPath) {
    // TODO den implement 
  }
}
