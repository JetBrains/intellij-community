// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.IndentStatusBarUIContributor;
import org.editorconfig.Utils;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorConfigIndentStatusBarUIContributor extends IndentStatusBarUIContributor {

  private final boolean myEditorConfigIndentOptions;

  public EditorConfigIndentStatusBarUIContributor(IndentOptions options) {
    super(options);
    myEditorConfigIndentOptions = options.getFileIndentOptionsProvider() instanceof EditorConfigIndentOptionsProvider;
  }

  @Override
  public boolean areActionsAvailable(@NotNull VirtualFile file) {
    return myEditorConfigIndentOptions;
  }

  @Override
  public AnAction @Nullable [] getActions(@NotNull PsiFile file) {
    if (myEditorConfigIndentOptions) {
      return EditorConfigActionUtil.createNavigationActions(file);
    }
    return null;
  }

  @Nullable
  @Override
  public AnAction createDisableAction(@NotNull Project project) {
    return EditorConfigActionUtil.createDisableAction(project, EditorConfigBundle.message("action.disable"));
  }

  @Nullable
  @Override
  public String getHint() {
    return myEditorConfigIndentOptions ? Utils.EDITOR_CONFIG_NAME : null;
  }

  @Override
  public boolean isShowFileIndentOptionsEnabled() {
    return false;
  }

}
