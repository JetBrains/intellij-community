package org.editorconfig.configmanagement.extended;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import org.editorconfig.configmanagement.EditorConfigActionUtil;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class EditorConfigCodeStyleStatusBarUIContributor implements CodeStyleStatusBarUIContributor {

  @Override
  public boolean areActionsAvailable(@NotNull VirtualFile file) {
    return true;
  }

  @Nullable
  @Override
  public AnAction[] getActions(@NotNull PsiFile file) {
    return EditorConfigActionUtil.createNavigationActions(file);
  }

  @Nullable
  @Override
  public String getTooltip() {
    return EditorConfigBundle.message("config.code.style.overridden");
  }

  @Nullable
  @Override
  public AnAction createDisableAction(@NotNull Project project) {
    return EditorConfigActionUtil.createDisableAction(project, EditorConfigBundle.message("action.disable"));
  }

  @NotNull
  @Override
  public String getStatusText(@NotNull PsiFile psiFile) {
    return EditorConfigBundle.message("config.title");
  }

  @Nullable
  @Override
  public AnAction createShowAllAction(@NotNull Project project) {
    return EditorConfigActionUtil.createShowEditorConfigFilesAction();
  }
}