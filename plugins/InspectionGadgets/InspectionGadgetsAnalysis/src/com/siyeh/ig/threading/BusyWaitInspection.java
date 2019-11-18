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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

public class BusyWaitInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("busy.wait.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("busy.wait.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BusyWaitVisitor();
  }

  private static class BusyWaitVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!MethodCallUtils.isCallToMethod(expression, "java.lang.Thread",
                                          PsiType.VOID, "sleep", PsiType.LONG) &&
          !MethodCallUtils.isCallToMethod(expression,
                                          "java.lang.Thread", PsiType.VOID, "sleep",
                                          PsiType.LONG, PsiType.INT)) {
        return;
      }
      PsiElement context = expression;
      while (true) {
        PsiLoopStatement loopStatement = PsiTreeUtil.getParentOfType(context, PsiLoopStatement.class, true,
                                                                     PsiClass.class, PsiLambdaExpression.class);
        if (loopStatement == null) return;
        context = loopStatement;
        PsiStatement body = loopStatement.getBody();
        if (!PsiTreeUtil.isAncestor(body, expression, true)) continue;
        PsiExpression loopCondition;
        if (loopStatement instanceof PsiWhileStatement) {
          loopCondition = ((PsiWhileStatement)loopStatement).getCondition();
        }
        else if (loopStatement instanceof PsiDoWhileStatement) {
          loopCondition = ((PsiDoWhileStatement)loopStatement).getCondition();
        }
        else if (loopStatement instanceof PsiForStatement) {
          loopCondition = ((PsiForStatement)loopStatement).getCondition();
        } else continue;
        if (ExpressionUtils.computeConstantExpression(loopCondition) == null && ExpressionUtils.isLocallyDefinedExpression(loopCondition)) {
          // Condition depends on locals only: likely they are changed in the loop (or another inspection should fire)
          // so this is not a classic busy wait.
          continue;
        }
        registerMethodCallError(expression);
        return;
      }
    }
  }
}