/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.testFramework;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class MockProblemDescriptor extends ProblemDescriptorImpl {
  public MockProblemDescriptor(@NotNull PsiElement psiElement,
                               String descriptionTemplate,
                               @NotNull ProblemHighlightType highlightType,
                               LocalQuickFix @NotNull ... fixes) {
    super(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false, null, true);
  }

  @Override
  protected void assertPhysical(@NotNull PsiElement startElement) {
  }
}
