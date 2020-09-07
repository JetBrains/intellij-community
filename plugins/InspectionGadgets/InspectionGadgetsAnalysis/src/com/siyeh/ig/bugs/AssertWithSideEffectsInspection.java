/*
 * Copyright 2009-2013 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MutationSignature;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AssertWithSideEffectsInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assert.with.side.effects.problem.descriptor", infos[0]);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertWithSideEffectsVisitor();
  }

  private static class AssertWithSideEffectsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssertStatement(PsiAssertStatement statement) {
      super.visitAssertStatement(statement);
      final PsiExpression condition = statement.getAssertCondition();
      if (condition == null) {
        return;
      }
      final SideEffectVisitor visitor = new SideEffectVisitor();
      condition.accept(visitor);
      String description = visitor.getSideEffectDescription();
      if (description == null) {
        return;
      }
      registerStatementError(statement, description);
    }
  }

  private static class SideEffectVisitor extends JavaRecursiveElementWalkingVisitor {
    private @Nls String sideEffectDescription;

    private @Nls String getSideEffectDescription() {
      return sideEffectDescription;
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      sideEffectDescription = expression.getLExpression().getText() + " " + expression.getOperationSign().getText() + " ...";
      stopWalking();
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      sideEffectDescription = getCallSideEffectDescription(expression);
      if (sideEffectDescription != null) {
        stopWalking();
      }
    }

    @Override
    public void visitUnaryExpression(PsiUnaryExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType)) {
        sideEffectDescription = expression.getText();
        stopWalking();
      } else {
        super.visitUnaryExpression(expression);
      }
    }
  }

  private static @Nullable @Nls String getCallSideEffectDescription(PsiMethodCallExpression call) {
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    if (JavaMethodContractUtil.isPure(method)) return null;
    MutationSignature signature = MutationSignature.fromMethod(method);
    if (signature.mutatesAnything()) {
      PsiExpression expression =
        signature.mutatedExpressions(call).filter(expr -> !ExpressionUtils.isNewObject(expr)).findFirst().orElse(null);
      if (expression != null) {
        return InspectionGadgetsBundle.message("assert.with.side.effects.call.mutates.expression", method.getName(), expression.getText());
      }
    }
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return null;
    }
    final MethodSideEffectVisitor visitor = new MethodSideEffectVisitor();
    body.accept(visitor);
    String description = visitor.getMutatedField();
    if (description != null) {
      return InspectionGadgetsBundle.message("assert.with.side.effects.call.mutates.field", method.getName(), description);
    }
    return null;
  }

  private static class MethodSideEffectVisitor extends JavaRecursiveElementWalkingVisitor {
    private String mutatedField;

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      checkExpression(expression.getLExpression());
      super.visitAssignmentExpression(expression);
    }

    @Override
    public void visitUnaryExpression(PsiUnaryExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType)) {
        checkExpression(expression.getOperand());
      }
      super.visitUnaryExpression(expression);
    }

    private void checkExpression(PsiExpression operand) {
      operand = PsiUtil.skipParenthesizedExprDown(operand);
      if (!(operand instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)operand;
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiField) {
        mutatedField = ((PsiField)target).getName();
        stopWalking();
      }
    }

    private String getMutatedField() {
      return mutatedField;
    }
  }
}