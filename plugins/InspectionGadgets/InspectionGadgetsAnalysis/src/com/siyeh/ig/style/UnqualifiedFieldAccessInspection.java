/*
 * Copyright 2006-2013 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddThisQualifierFix;
import org.jetbrains.annotations.NotNull;

public class UnqualifiedFieldAccessInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unqualified.field.access.display.name");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnqualifiedFieldAccessVisitor();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unqualified.field.access.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new AddThisQualifierFix();
  }

  private static class UnqualifiedFieldAccessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiExpression qualifierExpression = expression.getQualifierExpression();
      if (qualifierExpression != null) {
        return;
      }
      final PsiReferenceParameterList parameterList = expression.getParameterList();
      if (parameterList == null) {
        return;
      }
      final PsiElement element = expression.resolve();
      if (!(element instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)element;
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass fieldClass = field.getContainingClass();
      if (fieldClass instanceof PsiAnonymousClass) {
        final PsiClass expressionClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
        if (expressionClass != null && !expressionClass.equals(fieldClass)) {
          // qualified this expression not possible for anonymous class
          return;
        }
      }
      registerError(expression);
    }
  }
}