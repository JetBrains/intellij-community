// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n.folding;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("IntentionDescriptionNotFoundInspection")
public class EditPropertyValueIntentionAction implements IntentionAction {
  @Override
  @NotNull
  public String getText() {
    return "Edit property value";
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return EditPropertyValueAction.isEnabled(editor);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    EditPropertyValueAction.doEdit(editor);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
