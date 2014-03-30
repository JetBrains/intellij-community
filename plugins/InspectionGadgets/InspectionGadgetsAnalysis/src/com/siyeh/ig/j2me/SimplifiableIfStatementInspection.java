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
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimplifiableIfStatementInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("simplifiable.if.statement.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableIfStatementVisitor();
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiIfStatement statement = (PsiIfStatement)infos[0];
    return InspectionGadgetsBundle.message("simplifiable.if.statement.problem.descriptor", 
                                           StringUtil.escapeXml(calculateReplacementStatement(statement)));
  }

  @Nullable
  @NonNls
  static String calculateReplacementStatement(PsiIfStatement statement) {
    final PsiStatement thenBranch = ControlFlowUtils.stripBraces(statement.getThenBranch());
    if (thenBranch == null) {
      return null;
    }
    PsiStatement elseBranch = statement.getElseBranch();
    if (elseBranch == null) {
      final PsiElement nextStatement = PsiTreeUtil.skipSiblingsForward(statement, PsiWhiteSpace.class);
      if (nextStatement instanceof PsiStatement) {
        elseBranch = (PsiStatement)nextStatement;
      }
    } else {
      elseBranch = ControlFlowUtils.stripBraces(elseBranch);
    }
    if (elseBranch == null) {
      return null;
    }
    final PsiExpression condition = statement.getCondition();
    if (condition == null) {
      return null;
    }
    if (thenBranch instanceof PsiReturnStatement && elseBranch instanceof PsiReturnStatement) {
      return calculateReplacementReturnStatement(thenBranch, elseBranch, condition);
    } else if (thenBranch instanceof PsiExpressionStatement && elseBranch instanceof PsiExpressionStatement) {
      final PsiExpressionStatement thenStatement = (PsiExpressionStatement)thenBranch;
      final PsiExpressionStatement elseStatement = (PsiExpressionStatement)elseBranch;
      final PsiExpression thenExpression = thenStatement.getExpression();
      final PsiExpression elseExpression = elseStatement.getExpression();
      if (!(thenExpression instanceof PsiAssignmentExpression) || !(elseExpression instanceof PsiAssignmentExpression)) {
        return null;
      }
      final PsiAssignmentExpression thenAssignment = (PsiAssignmentExpression)thenExpression;
      final PsiAssignmentExpression elseAssignment = (PsiAssignmentExpression)elseExpression;
      return calculateReplacementAssignmentStatement(thenAssignment, elseAssignment, condition);
    }
    return null;
  }

  private static String calculateReplacementAssignmentStatement(PsiAssignmentExpression thenAssignment,
                                                                PsiAssignmentExpression elseAssignment, PsiExpression condition) {
    final PsiExpression lhs = thenAssignment.getLExpression();
    final PsiExpression thenRhs = thenAssignment.getRExpression();
    if (thenRhs == null) {
      return "";
    }
    final PsiExpression elseRhs = elseAssignment.getRExpression();
    if (elseRhs == null) {
      return "";
    }
    final PsiJavaToken token = elseAssignment.getOperationSign();
    if (BoolUtils.isTrue(thenRhs)) {
      return lhs.getText() + ' ' + token.getText() + ' ' +
             buildExpressionText(condition, ParenthesesUtils.OR_PRECEDENCE) + " || " +
             buildExpressionText(elseRhs, ParenthesesUtils.OR_PRECEDENCE) + ';';
    }
    else if (BoolUtils.isFalse(thenRhs)) {
      return lhs.getText() + ' ' + token.getText() + ' ' +
             buildNegatedExpressionText(condition, ParenthesesUtils.AND_PRECEDENCE) + " && " +
             buildExpressionText(elseRhs, ParenthesesUtils.AND_PRECEDENCE) + ';';
    }
    if (BoolUtils.isTrue(elseRhs)) {
      return lhs.getText() + ' ' + token.getText() + ' ' +
             buildNegatedExpressionText(condition, ParenthesesUtils.OR_PRECEDENCE) + " || " +
             buildExpressionText(thenRhs, ParenthesesUtils.OR_PRECEDENCE) + ';';
    }
    else {
      return lhs.getText() + ' ' + token.getText() + ' ' +
             buildExpressionText(condition, ParenthesesUtils.AND_PRECEDENCE) + " && " +
             buildExpressionText(thenRhs, ParenthesesUtils.AND_PRECEDENCE) + ';';
    }
  }

  @NonNls
  private static String calculateReplacementReturnStatement(PsiStatement thenBranch, PsiStatement elseBranch, PsiExpression condition) {
    final PsiReturnStatement thenReturnStatement = (PsiReturnStatement)thenBranch;
    final PsiExpression thenReturnValue = thenReturnStatement.getReturnValue();
    if (thenReturnValue == null) {
      return "";
    }
    final PsiReturnStatement elseReturnStatement = (PsiReturnStatement)elseBranch;
    final PsiExpression elseReturnValue = elseReturnStatement.getReturnValue();
    if (elseReturnValue == null) {
      return "";
    }
    if (BoolUtils.isTrue(thenReturnValue)) {
      return "return " + buildExpressionText(condition, ParenthesesUtils.OR_PRECEDENCE) + " || " +
             buildExpressionText(elseReturnValue, ParenthesesUtils.OR_PRECEDENCE) + ';';
    }
    else if (BoolUtils.isFalse(thenReturnValue)) {
      return "return " + buildNegatedExpressionText(condition, ParenthesesUtils.AND_PRECEDENCE) + " && " +
             buildExpressionText(elseReturnValue, ParenthesesUtils.AND_PRECEDENCE) + ';';
    }
    if (BoolUtils.isTrue(elseReturnValue)) {
      return "return " + buildNegatedExpressionText(condition, ParenthesesUtils.OR_PRECEDENCE) + " || " +
             buildExpressionText(thenReturnValue, ParenthesesUtils.OR_PRECEDENCE) + ';';
    }
    else {
      return "return " + buildExpressionText(condition, ParenthesesUtils.AND_PRECEDENCE) + " && " +
             buildExpressionText(thenReturnValue, ParenthesesUtils.AND_PRECEDENCE) + ';';
    }
  }

  private static String buildExpressionText(PsiExpression expression, int precedence) {
    final StringBuilder builder = new StringBuilder();
    if (ParenthesesUtils.getPrecedence(expression) > precedence) {
      builder.append('(');
      appendPresentableText(expression, builder);
      builder.append(')');
    }
    else {
      appendPresentableText(expression, builder);
    }
    return builder.toString();
  }

  private static void appendPresentableText(@Nullable PsiElement element, StringBuilder builder) {
    if (element == null) {
      return;
    }
    if (element instanceof PsiWhiteSpace) {
      final PsiElement prevSibling = element.getPrevSibling();
      if (prevSibling instanceof PsiComment) {
        final PsiComment comment = (PsiComment)prevSibling;
        if (JavaTokenType.END_OF_LINE_COMMENT.equals(comment.getTokenType())) {
          builder.append('\n');
          return;
        }
      }
      builder.append(' ');
      return;
    }
    final PsiElement[] children = element.getChildren();
    if (children.length == 0) {
      builder.append(element.getText());
    }
    else {
      for (PsiElement child : children) {
        appendPresentableText(child, builder);
      }
    }
  }


  public static String buildNegatedExpressionText(@Nullable PsiExpression expression, int precedence) {
    while (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      expression = parenthesizedExpression.getExpression();
    }
    if (expression == null) {
      return "";
    }
    final StringBuilder result = new StringBuilder();
    if (BoolUtils.isNegation(expression)) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      final PsiExpression operand = prefixExpression.getOperand();
      final PsiExpression negated = ParenthesesUtils.stripParentheses(operand);
      if (negated == null) {
        return "";
      }
      if (ParenthesesUtils.getPrecedence(negated) > precedence) {
        result.append('(');
        appendPresentableText(negated, result);
        result.append(')');
      }
      else {
        appendPresentableText(negated, result);
      }
    }
    else if (ComparisonUtils.isComparison(expression)) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final String negatedComparison = ComparisonUtils.getNegatedComparison(polyadicExpression.getOperationTokenType());
      final PsiExpression[] operands = polyadicExpression.getOperands();
      final boolean isEven = (operands.length & 1) != 1;
      for (int i = 0, length = operands.length; i < length; i++) {
        final PsiExpression operand = operands[i];
        if (i > 0) {
          if (isEven && (i & 1) != 1) {
            final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(operand);
            if (token != null) {
              result.append(token.getText());
            }
          }
          else {
            result.append(negatedComparison);
          }
        }
        appendPresentableText(operand, result);
      }
    }
    else if (ParenthesesUtils.getPrecedence(expression) > ParenthesesUtils.PREFIX_PRECEDENCE) {
      result.append("!(");
      appendPresentableText(expression, result);
      result.append(')');
    }
    else {
      result.append('!');
      appendPresentableText(expression, result);
    }
    return result.toString();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new SimplifiableIfStatementFix();
  }

  private static class SimplifiableIfStatementFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiIfStatement ifStatement = (PsiIfStatement)element.getParent();
      final String newStatement = calculateReplacementStatement(ifStatement);
      if (newStatement == null) {
        return;
      }
      if (ifStatement.getElseBranch() == null) {
        final PsiElement nextStatement = PsiTreeUtil.skipSiblingsForward(ifStatement, PsiWhiteSpace.class);
        if (nextStatement != null) {
          nextStatement.delete();
        }
      }
      PsiReplacementUtil.replaceStatement(ifStatement, newStatement);
    }
  }

  private static class SimplifiableIfStatementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      super.visitIfStatement(statement);
      if (statement.getCondition() == null) {
        return;
      }
      if (!(isReplaceableAssignment(statement) || isReplaceableReturn(statement))) {
        return;
      }
      registerStatementError(statement, statement);
    }

    public static boolean isReplaceableReturn(PsiIfStatement ifStatement) {
      PsiStatement thenBranch = ifStatement.getThenBranch();
      thenBranch = ControlFlowUtils.stripBraces(thenBranch);
      PsiStatement elseBranch = ifStatement.getElseBranch();
      elseBranch = ControlFlowUtils.stripBraces(elseBranch);
      if (elseBranch == null) {
        final PsiElement nextStatement = PsiTreeUtil.skipSiblingsForward(ifStatement, PsiWhiteSpace.class);
        if (nextStatement instanceof PsiStatement) {
          elseBranch = (PsiStatement)nextStatement;
        }
      }
      if (!(thenBranch instanceof PsiReturnStatement) || !(elseBranch instanceof PsiReturnStatement)) {
        return false;
      }
      final PsiExpression thenReturn = ((PsiReturnStatement)thenBranch).getReturnValue();
      if (thenReturn == null) {
        return false;
      }
      final PsiType thenType = thenReturn.getType();
      if (!PsiType.BOOLEAN.equals(thenType)) {
        return false;
      }
      final PsiExpression elseReturn = ((PsiReturnStatement)elseBranch).getReturnValue();
      if (elseReturn == null) {
        return false;
      }
      final PsiType elseType = elseReturn.getType();
      if (!PsiType.BOOLEAN.equals(elseType)) {
        return false;
      }
      final boolean thenConstant = BoolUtils.isFalse(thenReturn) || BoolUtils.isTrue(thenReturn);
      final boolean elseConstant = BoolUtils.isFalse(elseReturn) || BoolUtils.isTrue(elseReturn);
      return thenConstant != elseConstant;
    }

    public static boolean isReplaceableAssignment(PsiIfStatement ifStatement) {
      PsiStatement thenBranch = ifStatement.getThenBranch();
      if (thenBranch == null) {
        return false;
      }
      thenBranch = ControlFlowUtils.stripBraces(thenBranch);
      if (thenBranch == null || !isAssignment(thenBranch)) {
        return false;
      }
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch == null) {
        return false;
      }
      elseBranch = ControlFlowUtils.stripBraces(elseBranch);
      if (elseBranch == null || !isAssignment(elseBranch)) {
        return false;
      }
      final PsiExpressionStatement thenStatement = (PsiExpressionStatement)thenBranch;
      final PsiAssignmentExpression thenExpression = (PsiAssignmentExpression)thenStatement.getExpression();
      final PsiExpressionStatement elseStatement = (PsiExpressionStatement)elseBranch;
      final PsiAssignmentExpression elseExpression = (PsiAssignmentExpression)elseStatement.getExpression();
      final IElementType elseTokenType = elseExpression.getOperationTokenType();
      if (!thenExpression.getOperationTokenType().equals(elseTokenType)) {
        return false;
      }
      final PsiExpression thenRhs = thenExpression.getRExpression();
      if (thenRhs == null) {
        return false;
      }
      final PsiType thenRhsType = thenRhs.getType();
      if (!PsiType.BOOLEAN.equals(thenRhsType)) {
        return false;
      }
      final PsiExpression elseRhs = elseExpression.getRExpression();
      if (elseRhs == null) {
        return false;
      }
      final PsiType elseRhsType = elseRhs.getType();
      if (!PsiType.BOOLEAN.equals(elseRhsType)) {
        return false;
      }
      final boolean thenConstant = BoolUtils.isFalse(thenRhs) || BoolUtils.isTrue(thenRhs);
      final boolean elseConstant = BoolUtils.isFalse(elseRhs) || BoolUtils.isTrue(elseRhs);
      if (thenConstant == elseConstant) {
        return false;
      }
      final PsiExpression thenLhs = thenExpression.getLExpression();
      final PsiExpression elseLhs = elseExpression.getLExpression();
      return EquivalenceChecker.expressionsAreEquivalent(thenLhs, elseLhs);
    }

    public static boolean isAssignment(@Nullable PsiStatement statement) {
      if (!(statement instanceof PsiExpressionStatement)) {
        return false;
      }
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
      final PsiExpression expression = expressionStatement.getExpression();
      return expression instanceof PsiAssignmentExpression;
    }
  }
}