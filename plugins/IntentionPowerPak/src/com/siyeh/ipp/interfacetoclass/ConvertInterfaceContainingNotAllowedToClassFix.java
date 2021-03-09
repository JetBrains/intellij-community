// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.interfacetoclass;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConvertInterfaceContainingNotAllowedToClassFix extends LocalQuickFixAndIntentionActionOnPsiElement implements PriorityAction {

  public ConvertInterfaceContainingNotAllowedToClassFix(@Nullable PsiClass aClass) {
    super(aClass);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @Nullable Editor editor,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiElement nameIdentifier = getNameIdentifier(startElement);
    if (nameIdentifier == null) return false;
    return new ConvertInterfaceToClassIntention().getElementPredicate().satisfiedBy(nameIdentifier);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiElement nameIdentifier = getNameIdentifier(startElement);
    if (nameIdentifier == null) return;
    new ConvertInterfaceToClassIntention().processIntention(nameIdentifier);
  }

  @Override
  public @NotNull Priority getPriority() {
    return Priority.LOW;
  }

  private static PsiIdentifier getNameIdentifier(@NotNull PsiElement element) {
    final PsiClass aClass = ObjectUtils.tryCast(element, PsiClass.class);
    if (aClass == null) return null;
    return aClass.getNameIdentifier();
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return new ConvertInterfaceToClassIntention().getText();
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return new ConvertInterfaceToClassIntention().getFamilyName();
  }
}
