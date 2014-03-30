/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.cloneable;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CloneUtils;
import org.jetbrains.annotations.NotNull;

public class CloneCallsConstructorsInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "clone.instantiates.objects.with.constructor.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "clone.instantiates.objects.with.constructor.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CloneCallsConstructorVisitor();
  }

  private static class CloneCallsConstructorVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!CloneUtils.isClone(method)) {
        return;
      }
      method.accept(new JavaRecursiveElementVisitor() {

        @Override
        public void visitNewExpression(
          @NotNull PsiNewExpression newExpression) {
          super.visitNewExpression(newExpression);
          final PsiExpression[] arrayDimensions =
            newExpression.getArrayDimensions();
          if (arrayDimensions.length != 0) {
            return;
          }
          if (newExpression.getArrayInitializer() != null) {
            return;
          }
          if (newExpression.getAnonymousClass() != null) {
            return;
          }
          if (PsiTreeUtil.getParentOfType(newExpression,
                                          PsiThrowStatement.class) != null) {
            return;
          }
          registerNewExpressionError(newExpression);
        }
      });
    }
  }
}