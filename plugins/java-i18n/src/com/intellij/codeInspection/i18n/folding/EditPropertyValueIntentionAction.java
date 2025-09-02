// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n.folding;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("IntentionDescriptionNotFoundInspection")
public final class EditPropertyValueIntentionAction implements IntentionAction {
  @Override
  public @NotNull String getText() {
    return JavaI18nBundle.message("intention.text.edit.property.value");
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return EditPropertyValueAction.isEnabled(editor);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    EditPropertyValueAction.doEdit(editor);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
