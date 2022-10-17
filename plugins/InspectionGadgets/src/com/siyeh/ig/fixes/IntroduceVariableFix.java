// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class IntroduceVariableFix extends RefactoringInspectionGadgetsFix {

  private final boolean myOnQualifier;

  public IntroduceVariableFix(boolean onQualifier) {
    myOnQualifier = onQualifier;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("introduce.variable.quickfix");
  }

  @NotNull
  @Override
  public RefactoringActionHandler getHandler() {
    return JavaRefactoringActionHandlerFactory.getInstance().createIntroduceVariableHandler();
  }

  @Override
  public PsiElement getElementToRefactor(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (myOnQualifier) {
      if (parent instanceof PsiReferenceExpression ref) {
        return ref.getQualifierExpression();
      }
    }
    else {
      if (parent instanceof PsiReferenceExpression) {
        final PsiElement grandParent = parent.getParent();
        return grandParent instanceof PsiMethodCallExpression ? grandParent : parent;
      }
    }
    return super.getElementToRefactor(element);
  }
}
