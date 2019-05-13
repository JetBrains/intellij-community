/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
      if (parent instanceof PsiReferenceExpression) {
        return ((PsiReferenceExpression)parent).getQualifierExpression();
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
