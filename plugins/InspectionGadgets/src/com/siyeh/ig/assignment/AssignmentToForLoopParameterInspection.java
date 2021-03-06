// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ExtractParameterAsLocalVariableFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class AssignmentToForLoopParameterInspection extends BaseInspection {

  /**
   * @noinspection PublicField for externalization purposes
   */
  public boolean m_checkForeachParameters = true;

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final Boolean foreachLoop = (Boolean)infos[0];
    if (!foreachLoop.booleanValue()) {
      return null;
    }
    return new ExtractParameterAsLocalVariableFix();
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assignment.to.for.loop.parameter.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("assignment.to.for.loop.parameter.check.foreach.option"),
                                          this, "m_checkForeachParameters");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssignmentToForLoopParameterVisitor();
  }

  private class AssignmentToForLoopParameterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (expression.getRExpression() == null) {
        return;
      }
      final PsiExpression lhs = expression.getLExpression();
      checkForForLoopParam(lhs);
      checkForForeachLoopParam(lhs);
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
          !tokenType.equals(JavaTokenType.MINUSMINUS)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      checkForForLoopParam(operand);
      checkForForeachLoopParam(operand);  //sensible due to autoboxing/unboxing
    }

    private void checkForForLoopParam(PsiExpression expression) {
      final PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable(expression);
      if (variable == null) {
        return;
      }
      final PsiElement variableParent = variable.getParent();
      if (!(variableParent instanceof PsiDeclarationStatement)) {
        return;
      }
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)variableParent;
      final PsiElement parent = declarationStatement.getParent();
      if (!(parent instanceof PsiForStatement)) {
        return;
      }
      final PsiForStatement forStatement = (PsiForStatement)parent;
      final PsiStatement initialization = forStatement.getInitialization();
      if (initialization == null ||
          !initialization.equals(declarationStatement) ||
          !isInForStatementBody(expression, forStatement) ||
          forStatement.getUpdate() == null) {
        return;
      }
      registerError(expression, Boolean.FALSE);
    }

    private void checkForForeachLoopParam(PsiExpression expression) {
      if (!m_checkForeachParameters) {
        return;
      }
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (!(expression instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      if (referenceExpression.getQualifierExpression() != null) {
        return;
      }
      PsiElement element = referenceExpression.resolve();
      if (!(element instanceof PsiParameter) || !(element.getParent() instanceof PsiForeachStatement)) {
        return;
      }
      registerError(expression, Boolean.TRUE);
    }

    private boolean isInForStatementBody(PsiExpression expression, PsiForStatement statement) {
      return PsiTreeUtil.isAncestor(statement.getBody(), expression, true);
    }
  }
}
