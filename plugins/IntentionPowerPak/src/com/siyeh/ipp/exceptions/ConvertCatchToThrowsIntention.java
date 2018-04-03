/*
 * Copyright 2007-2018 Bas Leijdekkers
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
package com.siyeh.ipp.exceptions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.fixes.DeleteCatchSectionFix;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ConvertCatchToThrowsIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new ConvertCatchToThrowsPredicate();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiCatchSection catchSection = (PsiCatchSection)element.getParent();
    final NavigatablePsiElement owner = PsiTreeUtil.getParentOfType(catchSection, PsiMethod.class, PsiLambdaExpression.class);
    final PsiMethod method;
    if (owner instanceof PsiMethod) {
      method = (PsiMethod)owner;
    }
    else if (owner instanceof PsiLambdaExpression) {
      method = LambdaUtil.getFunctionalInterfaceMethod(owner);
    }
    else {
      return;
    }
    if (method == null || !FileModificationService.getInstance().preparePsiElementsForWrite(method)) {
      return;
    }
    // todo warn if method implements or overrides some base method
    //             Warning
    // "Method xx() of class XX implements/overrides method of class
    // YY. Do you want to modify the base method?"
    //                                             [Yes][No][Cancel]
    WriteAction.run(() -> {
      addToThrowsList(method.getThrowsList(), catchSection.getCatchType());
      final PsiTryStatement tryStatement = catchSection.getTryStatement();
      if (tryStatement.getCatchSections().length > 1 || tryStatement.getResourceList() != null || tryStatement.getFinallyBlock() != null) {
        catchSection.delete();
      }
      else {
        DeleteCatchSectionFix.unwrapTryBlock(tryStatement);
      }
    });
  }

  private static void addToThrowsList(PsiReferenceList throwsList, PsiType catchType) {
    if (catchType instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType)catchType;
      final PsiClassType[] types = throwsList.getReferencedTypes();
      for (PsiClassType type : types) {
        if (catchType.equals(type)) {
          return;
        }
      }
      final Project project = throwsList.getProject();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiJavaCodeReferenceElement referenceElement = factory.createReferenceElementByType(classType);
      throwsList.add(referenceElement);
    }
    else if (catchType instanceof PsiDisjunctionType) {
      final PsiDisjunctionType disjunctionType = (PsiDisjunctionType)catchType;
      final List<PsiType> disjunctions = disjunctionType.getDisjunctions();
      for (PsiType disjunction : disjunctions) {
        addToThrowsList(throwsList, disjunction);
      }
    }
  }
}