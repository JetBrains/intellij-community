/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.initialization;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;

class NonFinalStaticVariableUsedInClassInitializationVisitor extends BaseInspectionVisitor {

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    super.visitReferenceExpression(expression);
    final PsiElement referent = expression.resolve();
    if (!(referent instanceof PsiField)) {
      return;
    }
    final PsiField field = (PsiField)referent;
    if (!field.hasModifierProperty(PsiModifier.STATIC)) {
      return;
    }
    if (field.hasModifierProperty(PsiModifier.FINAL)) {
      return;
    }
    if (!isInClassInitialization(expression)) {
      return;
    }
    registerError(expression, field);
  }

  private static boolean isInClassInitialization(
    PsiExpression expression) {
    final PsiClass expressionClass =
      ClassUtils.getContainingClass(expression);
    final PsiMember member =
      PsiTreeUtil.getParentOfType(expression,
                                  PsiClassInitializer.class, PsiField.class);
    if (member == null) {
      return false;
    }
    final PsiClass memberClass = member.getContainingClass();
    if (!memberClass.equals(expressionClass)) {
      return false;
    }
    if (!member.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    if (member instanceof PsiClassInitializer) {
      return !PsiUtil.isOnAssignmentLeftHand(expression);
    }
    return true;
  }
}