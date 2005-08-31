/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.methodmetrics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class MultipleReturnPointsPerMethodInspection extends MethodMetricInspection {

  public String getID() {
    return "MethodWithMultipleReturnPoints";
  }

  public String getGroupDisplayName() {
    return GroupNames.METHODMETRICS_GROUP_NAME;
  }

  protected int getDefaultLimit() {
    return 1;
  }

  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message("return.point.limit.option");
  }

  public String buildErrorString(PsiElement location) {
    final PsiMethod parent = (PsiMethod)location.getParent();
    final int numReturnPoints = calculateNumReturnPoints(parent);
    return InspectionGadgetsBundle.message("multiple.return.points.per.method.problem.descriptor", numReturnPoints);
  }

  public BaseInspectionVisitor buildVisitor() {
    return new MultipleReturnPointsVisitor();
  }

  private class MultipleReturnPointsVisitor extends BaseInspectionVisitor {

    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      final int numReturnPoints = calculateNumReturnPoints(method);
      if (numReturnPoints <= getLimit()) {
        return;
      }
      registerMethodError(method);
    }

  }

  private static int calculateNumReturnPoints(PsiMethod method) {
    final ReturnPointCountVisitor visitor = new ReturnPointCountVisitor();
    method.accept(visitor);
    final int count = visitor.getCount();

    if (!mayFallThroughBottom(method)) {
      return count;
    }
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return count;
    }
    final PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) {
      return count + 1;
    }
    final PsiStatement lastStatement = statements[statements.length - 1];
    if (ControlFlowUtils.statementMayCompleteNormally(lastStatement)) {
      return count + 1;
    }
    return count;
  }

  private static boolean mayFallThroughBottom(PsiMethod method) {
    if (method.isConstructor()) {
      return true;
    }
    final PsiType returnType = method.getReturnType();
    return PsiType.VOID.equals(returnType);
  }
}
