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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

public class ObjectAllocationInLoopInspection extends BaseInspection {
  enum Kind {
    NEW_OPERATOR("object.allocation.in.loop.new.descriptor"),
    METHOD_CALL("object.allocation.in.loop.problem.call.descriptor"),
    METHOD_REFERENCE("object.allocation.in.loop.problem.methodref.descriptor"),
    CAPTURING_LAMBDA("object.allocation.in.loop.problem.lambda.descriptor");

    private final String myMessage;

    Kind(@PropertyKey(resourceBundle = InspectionGadgetsBundle.BUNDLE) String message) {
      myMessage = InspectionGadgetsBundle.message(message);
    }

    @Override
    public String toString() {
      return myMessage;
    }
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "object.allocation.in.loop.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    Kind kind = (Kind)infos[0];
    return kind.toString();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObjectAllocationInLoopsVisitor();
  }

  private static class ObjectAllocationInLoopsVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      PsiMethod method = call.resolveMethod();
      if (method != null) {
        List<StandardMethodContract> contracts = JavaMethodContractUtil.getMethodContracts(method);
        ContractReturnValue value = JavaMethodContractUtil.getNonFailingReturnValue(contracts);
        if (ContractReturnValue.returnNew().equals(value) && isPerformedRepeatedlyInLoop(call)) {
          registerMethodCallError(call, Kind.METHOD_CALL);
        }
      }
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      super.visitMethodReferenceExpression(expression);
      if (!PsiMethodReferenceUtil.isStaticallyReferenced(expression) &&
          isPerformedRepeatedlyInLoop(expression)) {
        registerError(expression, Kind.METHOD_REFERENCE);
      }
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression lambda) {
      super.visitLambdaExpression(lambda);
      if (isPerformedRepeatedlyInLoop(lambda) && LambdaUtil.isCapturingLambda(lambda)) {
        registerError(lambda.getParameterList(), Kind.CAPTURING_LAMBDA);
      }
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (isPerformedRepeatedlyInLoop(expression)) {
        registerNewExpressionError(expression, Kind.NEW_OPERATOR);
      }
    }

    private static boolean isPerformedRepeatedlyInLoop(@NotNull PsiExpression expression) {
      if (!ControlFlowUtils.isInLoop(expression)) return false;
      if (ControlFlowUtils.isInExitStatement(expression)) return false;
      final PsiStatement newExpressionStatement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
      if (newExpressionStatement == null) return false;
      final PsiStatement parentStatement = PsiTreeUtil.getParentOfType(newExpressionStatement, PsiStatement.class);
      if (!ControlFlowUtils.statementMayCompleteNormally(parentStatement)) return false;
      return !isAllocatedOnlyOnce(expression);
    }

    private static boolean isAllocatedOnlyOnce(PsiExpression expression) {
      final PsiAssignmentExpression assignment =
        PsiTreeUtil.getParentOfType(expression, PsiAssignmentExpression.class, true, PsiStatement.class);
      if (assignment == null) return false;
      final PsiReferenceExpression assignedRef = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
      if (assignedRef == null) return false;
      // to support cases like if(foo == null) foo = new Foo(new Bar());
      if (assignment.getRExpression() != expression &&
          NullabilityUtil.getExpressionNullability(assignment.getRExpression()) != Nullability.NOT_NULL) {
        return false;
      }
      final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(assignment, PsiIfStatement.class);
      if (ifStatement == null) return false;
      boolean equals;
      if (PsiTreeUtil.isAncestor(ifStatement.getThenBranch(), assignment, true)) {
        equals = true;
      }
      else if (PsiTreeUtil.isAncestor(ifStatement.getElseBranch(), assignment, true)) {
        equals = false;
      }
      else {
        return false;
      }
      final PsiExpression condition = ifStatement.getCondition();
      PsiReferenceExpression nullCheckedRef = ExpressionUtils.getReferenceExpressionFromNullComparison(condition, equals);
      return nullCheckedRef != null && PsiEquivalenceUtil.areElementsEquivalent(nullCheckedRef, assignedRef);
    }
  }
}