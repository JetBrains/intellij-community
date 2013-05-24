/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.j2me;

import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class ArrayLengthInLoopConditionInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "array.length.in.loop.condition.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "array.length.in.loop.condition.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayLengthInLoopConditionVisitor();
  }

  private static class ArrayLengthInLoopConditionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      checkForMethodCalls(condition);
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      checkForMethodCalls(condition);
    }

    @Override
    public void visitDoWhileStatement(
      @NotNull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      checkForMethodCalls(condition);
    }

    private void checkForMethodCalls(PsiExpression condition) {
      final PsiElementVisitor visitor =
        new JavaRecursiveElementVisitor() {

          @Override
          public void visitReferenceExpression(
            @NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final String name = expression.getReferenceName();
            if (!HardcodedMethodConstants.LENGTH.equals(name)) {
              return;
            }
            final PsiExpression qualifier =
              expression.getQualifierExpression();
            if (qualifier == null) {
              return;
            }
            final PsiType type = qualifier.getType();
            if (!(type instanceof PsiArrayType)) {
              return;
            }
            final PsiElement lengthElement =
              expression.getReferenceNameElement();
            if (lengthElement == null) {
              return;
            }
            registerError(lengthElement);
          }
        };
      condition.accept(visitor);
    }
  }
}