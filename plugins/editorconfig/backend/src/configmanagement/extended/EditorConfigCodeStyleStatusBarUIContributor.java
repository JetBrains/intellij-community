// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.extended;

import com.intellij.application.options.CodeStyle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.IndentStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import org.editorconfig.configmanagement.EditorConfigActionUtil;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class EditorConfigCodeStyleStatusBarUIContributor implements CodeStyleStatusBarUIContributor {

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
    return EditorConfigBundle.message("config.code.style.overridden");
  }

  @Override
  public @Nullable AnAction createDisableAction(@NotNull Project project) {
    return EditorConfigActionUtil.createDisableAction(project, EditorConfigBundle.message("action.disable"));
  }

  @Override
  public @NotNull String getStatusText(@NotNull PsiFile psiFile) {
    IndentOptions fileOptions = CodeStyle.getSettings(psiFile).getIndentOptionsByFile(psiFile);
    String indentInfo = IndentStatusBarUIContributor.getIndentInfo(fileOptions);
    IndentOptions projectOptions = CodeStyle.getSettings(psiFile.getProject()).getIndentOptions(psiFile.getFileType());
    if (projectOptions.INDENT_SIZE != fileOptions.INDENT_SIZE || projectOptions.USE_TAB_CHARACTER != fileOptions.USE_TAB_CHARACTER) {
      indentInfo += "*";
    }
    return indentInfo;
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