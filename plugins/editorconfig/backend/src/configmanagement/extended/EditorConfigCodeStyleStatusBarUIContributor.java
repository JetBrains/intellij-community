// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.extended;

import com.intellij.application.options.CodeStyle;
import com.intellij.editorconfig.common.EditorConfigBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.IndentStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import org.editorconfig.configmanagement.EditorConfigActionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class EditorConfigCodeStyleStatusBarUIContributor implements CodeStyleStatusBarUIContributor {
  private IndentOptions myIndentOptionsForFileInEditor;

  @Override
  public boolean areActionsAvailable(@NotNull VirtualFile file) {
    return true;
  }

  @Override
  public AnAction @Nullable [] getActions(@NotNull PsiFile file) {
    return EditorConfigActionUtil.createNavigationActions(file);
  }

  @Override
  public @Nullable String getActionGroupTitle() {
    return EditorConfigBundle.message("action.group.title");
  }

  @Override
  public @Nullable String getTooltip() {
    if (myIndentOptionsForFileInEditor == null) return null; // not ready yet
    return IndentStatusBarUIContributor.createTooltip(
      IndentStatusBarUIContributor.getIndentInfo(myIndentOptionsForFileInEditor), 
      getActionGroupTitle());
  }

  @Override
  public @Nullable AnAction createDisableAction(@NotNull Project project) {
    return EditorConfigActionUtil.createDisableAction(project, EditorConfigBundle.message("action.disable"));
  }

  @Override
  public @NotNull String getStatusText(@NotNull PsiFile psiFile) {
    myIndentOptionsForFileInEditor = CodeStyle.getSettings(psiFile).getIndentOptionsByFile(psiFile);
    return IndentStatusBarUIContributor.getIndentInfo(myIndentOptionsForFileInEditor);
  }

  @Override
  public @Nullable AnAction createShowAllAction(@NotNull Project project) {
    return EditorConfigActionUtil.createShowEditorConfigFilesAction();
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Ide.ConfigFile;
  }
}