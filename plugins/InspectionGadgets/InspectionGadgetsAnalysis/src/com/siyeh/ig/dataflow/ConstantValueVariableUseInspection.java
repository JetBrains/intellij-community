/*
 * Copyright 2008-2015 Bas Leijdekkers
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
package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConstantValueVariableUseInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "constant.value.variable.use.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "constant.value.variable.use.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    return new ReplaceReferenceWithExpressionFix(expression.getText());
  }

  private static class ReplaceReferenceWithExpressionFix extends InspectionGadgetsFix {
    private final String myText;

    ReplaceReferenceWithExpressionFix(String text) {
      myText = text;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "replace.reference.with.expression.quickfix",
        myText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiExpression)) {
        return;
      }
      final PsiExpression expression = (PsiExpression)element;
      PsiReplacementUtil.replaceExpression(expression, myText);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantValueVariableUseVisitor();
  }

  private static class ConstantValueVariableUseVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiExpression condition = statement.getCondition();
      final PsiStatement body = statement.getThenBranch();
      checkCondition(condition, body);
    }

    @Override
    public void visitWhileStatement(PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      final PsiExpression condition = statement.getCondition();
      final PsiStatement body = statement.getBody();
      checkCondition(condition, body);
    }

    @Override
    public void visitForStatement(PsiForStatement statement) {
      super.visitForStatement(statement);
      final PsiExpression condition = statement.getCondition();
      final PsiStatement body = statement.getBody();
      checkCondition(condition, body);
    }

    private boolean checkCondition(@Nullable PsiExpression condition,
                                   @Nullable PsiStatement body) {
      if (body == null) {
        return false;
      }
      if (!(condition instanceof PsiPolyadicExpression)) {
        return false;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.ANDAND == tokenType) {
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          if (checkCondition(operand, body)) {
            return true;
          }
        }
        return false;
      }
      if (JavaTokenType.EQEQ != tokenType) {
        return false;
      }
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (operands.length != 2) {
        return false;
      }
      final PsiExpression lhs = operands[0];
      final PsiExpression rhs = operands[1];
      if (PsiUtil.isConstantExpression(lhs)) {
        return checkConstantValueVariableUse(rhs, lhs, body);
      }
      else if (PsiUtil.isConstantExpression(rhs)) {
        return checkConstantValueVariableUse(lhs, rhs, body);
      }
      return false;
    }

    private boolean checkConstantValueVariableUse(
      @Nullable PsiExpression expression,
      @NotNull PsiExpression constantExpression,
      @NotNull PsiElement body) {
      final PsiType constantType = constantExpression.getType();
      if (PsiType.DOUBLE.equals(constantType)) {
        final Object result = ExpressionUtils.computeConstantExpression(
          constantExpression, false);
        if (Double.valueOf(0.0).equals(result) ||
            Double.valueOf(-0.0).equals(result)) {
          return false;
        }
      }
      expression = ParenthesesUtils.stripParentheses(expression);
      if (!(expression instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)expression;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        return false;
      }
      if (target instanceof PsiField) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)target;
      final VariableReadVisitor visitor = new VariableReadVisitor(variable);
      body.accept(visitor);
      if (!visitor.isRead()) {
        return false;
      }
      registerError(visitor.getReference(), constantExpression);
      return true;
    }
  }

  private static class VariableReadVisitor extends JavaRecursiveElementWalkingVisitor {

    @NotNull
    private final PsiVariable variable;
    private boolean read = false;
    private boolean stop = false;
    private PsiReferenceExpression reference = null;

    VariableReadVisitor(@NotNull PsiVariable variable) {
      this.variable = variable;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (read || stop) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (read || stop) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (variable.equals(target)) {
        if (PsiUtil.isAccessedForWriting(expression)) {
          stop = true;
          return;
        }
        if (PsiUtil.isAccessedForReading(expression)) {
          if (isInLoopCondition(expression)) {
            stop = true;
          }
          else {
            reference = expression;
            read = true;
          }
          return;
        }
      }
      super.visitReferenceExpression(expression);
    }

    private static boolean isInLoopCondition(PsiExpression expression) {
      final PsiStatement statement =
        PsiTreeUtil.getParentOfType(expression, PsiStatement.class, true, PsiMember.class, PsiLambdaExpression.class);
      return statement instanceof PsiLoopStatement;
    }

    public boolean isRead() {
      return read;
    }

    public PsiReferenceExpression getReference() {
      return reference;
    }
  }
}
