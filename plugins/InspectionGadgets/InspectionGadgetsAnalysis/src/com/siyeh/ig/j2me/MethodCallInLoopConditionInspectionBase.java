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
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MethodCallInLoopConditionInspectionBase extends BaseInspection {

  public boolean ignoreIterationMethods = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("method.call.in.loop.condition.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("method.call.in.loop.condition.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Ignore iteration method calls", this, "ignoreIterationMethods");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodCallInLoopConditionVisitor();
  }

  private class MethodCallInLoopConditionVisitor extends BaseInspectionVisitor {

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
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      checkForMethodCalls(condition);
    }

    private void checkForMethodCalls(PsiExpression condition) {
      final PsiElementVisitor visitor = new JavaRecursiveElementWalkingVisitor() {

          @Override
          public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (ignoreIterationMethods) {
              final PsiMethod method = expression.resolveMethod();
              if (MethodCallUtils.isCallToMethod(expression, CommonClassNames.JAVA_UTIL_ITERATOR, PsiType.BOOLEAN, "hasNext") ||
                  MethodCallUtils.isCallToMethod(expression, "java.util.ListIterator", PsiType.BOOLEAN, "hasPrevious") ||
                  MethodCallUtils.isCallToMethod(expression, "java.sql.ResultSet", PsiType.BOOLEAN, "next") ||
                  MethodCallUtils.isCallToMethod(expression, "java.util.Enumeration", PsiType.BOOLEAN, "hasMoreElements")) {
                return;
              }
            }
            registerMethodCallError(expression);
          }
        };
      condition.accept(visitor);
    }
  }
}
