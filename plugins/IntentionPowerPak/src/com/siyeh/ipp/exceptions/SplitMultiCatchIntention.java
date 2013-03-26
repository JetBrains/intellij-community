/*
 * Copyright 2011 Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.PsiAnnotation.TargetType;

public class SplitMultiCatchIntention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MulticatchPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiCatchSection)) {
      return;
    }
    final PsiCatchSection catchSection = (PsiCatchSection)parent;
    final PsiElement grandParent = catchSection.getParent();
    if (!(grandParent instanceof PsiTryStatement)) {
      return;
    }
    final PsiParameter parameter = catchSection.getParameter();
    if (parameter == null) {
      return;
    }
    final PsiType type = parameter.getType();
    if (!(type instanceof PsiDisjunctionType)) {
      return;
    }

    final PsiModifierList modifierList = parameter.getModifierList();
    if (modifierList != null) {
      for (PsiAnnotation annotation : modifierList.getAnnotations()) {
        if (PsiImplUtil.findApplicableTarget(annotation, TargetType.TYPE_USE) == TargetType.TYPE_USE) {
          annotation.delete();
        }
      }
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    for (PsiType disjunction : ((PsiDisjunctionType)type).getDisjunctions()) {
      final PsiCatchSection copy = (PsiCatchSection)catchSection.copy();
      final PsiParameter copyParameter = copy.getParameter();
      assert copyParameter != null : copy.getText();
      final PsiTypeElement typeElement = copyParameter.getTypeElement();
      assert typeElement != null : copyParameter.getText();
      final PsiTypeElement newTypeElement = factory.createTypeElement(disjunction);
      typeElement.replace(newTypeElement);
      grandParent.addBefore(copy, catchSection);
    }

    catchSection.delete();
  }
}
