/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.RefactoringQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class EncapsulateVariableFix extends RefactoringInspectionGadgetsFix implements RefactoringQuickFix {

  private final String fieldName;

  public EncapsulateVariableFix(String fieldName) {
    this.fieldName = fieldName;
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message("encapsulate.variable.quickfix", fieldName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("encapsulate.variable.fix.family.name");
  }

  @Override
  public PsiElement getElementToRefactor(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression ref) {
      final PsiElement target = ref.resolve();
      assert target instanceof PsiField;
      return target;
    }
    else {
      return super.getElementToRefactor(element);
    }
  }

  @NotNull
  @Override
  public RefactoringActionHandler getHandler() {
    return JavaRefactoringActionHandlerFactory.getInstance().createEncapsulateFieldsHandler();
  }
}
