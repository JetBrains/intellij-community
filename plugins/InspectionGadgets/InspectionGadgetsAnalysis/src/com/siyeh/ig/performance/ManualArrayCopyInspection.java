/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManualArrayCopyInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "manual.array.copy.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "manual.array.copy.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ManualArrayCopyVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final Boolean decrement = (Boolean)infos[0];
    return new ManualArrayCopyFix(decrement.booleanValue());
  }

  private static class ManualArrayCopyFix extends InspectionGadgetsFix {

    private final boolean decrement;

    public ManualArrayCopyFix(boolean decrement) {
      this.decrement = decrement;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("manual.array.copy.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement forElement = descriptor.getPsiElement();
      final PsiForStatement forStatement = (PsiForStatement)forElement.getParent();
      CommentTracker commentTracker = new CommentTracker();
      final String newExpression = buildSystemArrayCopyText(forStatement, commentTracker);
      if (newExpression == null) {
        return;
      }
      PsiReplacementUtil.replaceStatement(forStatement, newExpression, commentTracker);
    }

    @Nullable
    private String buildSystemArrayCopyText(PsiForStatement forStatement, CommentTracker commentTracker) {
      final PsiExpression condition = forStatement.getCondition();
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)ParenthesesUtils.stripParentheses(condition);
      if (binaryExpression == null) {
        return null;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      final PsiExpression limit;
      if (decrement ^ JavaTokenType.LT.equals(tokenType) || JavaTokenType.LE.equals(tokenType)) {
        limit = binaryExpression.getROperand();
      }
      else {
        limit = binaryExpression.getLOperand();
      }
      if (limit == null) {
        return null;
      }
      final PsiStatement initialization = forStatement.getInitialization();
      if (initialization == null) {
        return null;
      }
      if (!(initialization instanceof PsiDeclarationStatement)) {
        return null;
      }
      final PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
      final PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length != 1) {
        return null;
      }
      final PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiLocalVariable)) {
        return null;
      }
      final PsiLocalVariable variable = (PsiLocalVariable)declaredElement;
      final String lengthText;
      final PsiExpression initializer = variable.getInitializer();
      if (decrement) {
        lengthText = buildLengthText(initializer, limit, JavaTokenType.LE.equals(tokenType) || JavaTokenType.GE.equals(tokenType), commentTracker);
      }
      else {
        lengthText = buildLengthText(limit, initializer, JavaTokenType.LE.equals(tokenType) || JavaTokenType.GE.equals(tokenType), commentTracker);
      }
      if (lengthText == null) {
        return null;
      }
      final PsiArrayAccessExpression lhs = getLhsArrayAccessExpression(forStatement);
      if (lhs == null) {
        return null;
      }
      final PsiExpression lArray = lhs.getArrayExpression();
      final String toArrayText = commentTracker.text(lArray);
      final PsiArrayAccessExpression rhs = getRhsArrayAccessExpression(forStatement);
      if (rhs == null) {
        return null;
      }
      final PsiExpression rArray = rhs.getArrayExpression();
      final String fromArrayText = commentTracker.text(rArray);
      final PsiExpression rhsIndexExpression = rhs.getIndexExpression();
      final PsiExpression strippedRhsIndexExpression = ParenthesesUtils.stripParentheses(rhsIndexExpression);
      final PsiExpression limitExpression;
      if (decrement) {
        limitExpression = limit;
      }
      else {
        limitExpression = initializer;
      }
      final String fromOffsetText = buildOffsetText(strippedRhsIndexExpression, variable, limitExpression, decrement &&
                                         (JavaTokenType.LT.equals(tokenType) || JavaTokenType.GT.equals(tokenType)), commentTracker);
      final PsiExpression lhsIndexExpression = lhs.getIndexExpression();
      final PsiExpression strippedLhsIndexExpression = ParenthesesUtils.stripParentheses(lhsIndexExpression);
      final String toOffsetText = buildOffsetText(strippedLhsIndexExpression, variable,
                                                  limitExpression, decrement && (JavaTokenType.LT.equals(tokenType) || JavaTokenType.GT.equals(tokenType)),
                                                  commentTracker);
      return "System.arraycopy(" + fromArrayText + ", " + fromOffsetText + ", " + toArrayText + ", " + toOffsetText + ", " + lengthText + ");";
    }

    @Nullable
    private static PsiArrayAccessExpression getLhsArrayAccessExpression(PsiForStatement forStatement) {
      PsiStatement body = forStatement.getBody();
      while (body instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 2) {
          body = statements[1];
        }
        else if (statements.length == 1) {
          body = statements[0];
        }
        else {
          return null;
        }
      }
      if (!(body instanceof PsiExpressionStatement)) {
        return null;
      }
      final PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement)body;
      final PsiExpression expression =
        expressionStatement.getExpression();
      if (!(expression instanceof PsiAssignmentExpression)) {
        return null;
      }
      final PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)expression;
      final PsiExpression lhs = assignmentExpression.getLExpression();

      final PsiExpression deparenthesizedExpression =
        ParenthesesUtils.stripParentheses(lhs);
      if (!(deparenthesizedExpression instanceof
              PsiArrayAccessExpression)) {
        return null;
      }
      return (PsiArrayAccessExpression)deparenthesizedExpression;
    }

    @Nullable
    private static PsiArrayAccessExpression getRhsArrayAccessExpression(
      PsiForStatement forStatement) {
      PsiStatement body = forStatement.getBody();
      while (body instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement =
          (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 1 || statements.length == 2) {
          body = statements[0];
        }
        else {
          return null;
        }
      }
      final PsiExpression arrayAccessExpression;
      if (body instanceof PsiDeclarationStatement) {
        final PsiDeclarationStatement declarationStatement =
          (PsiDeclarationStatement)body;
        final PsiElement[] declaredElements =
          declarationStatement.getDeclaredElements();
        if (declaredElements.length != 1) {
          return null;
        }
        final PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable)) {
          return null;
        }
        final PsiVariable variable = (PsiVariable)declaredElement;
        arrayAccessExpression = variable.getInitializer();
      }
      else if (body instanceof PsiExpressionStatement) {
        final PsiExpressionStatement expressionStatement =
          (PsiExpressionStatement)body;
        final PsiExpression expression =
          expressionStatement.getExpression();
        if (!(expression instanceof PsiAssignmentExpression)) {
          return null;
        }
        final PsiAssignmentExpression assignmentExpression =
          (PsiAssignmentExpression)expression;
        arrayAccessExpression = assignmentExpression.getRExpression();
      }
      else {
        return null;
      }
      final PsiExpression unparenthesizedExpression =
        ParenthesesUtils.stripParentheses(arrayAccessExpression);
      if (!(unparenthesizedExpression instanceof
              PsiArrayAccessExpression)) {
        return null;
      }
      return (PsiArrayAccessExpression)unparenthesizedExpression;
    }

    @NonNls
    @Nullable
    private static String buildLengthText(PsiExpression max,
                                          PsiExpression min,
                                          boolean plusOne,
                                          CommentTracker commentTracker) {
      max = ParenthesesUtils.stripParentheses(max);
      if (max == null) {
        return null;
      }
      min = ParenthesesUtils.stripParentheses(min);
      if (min == null) {
        return buildExpressionText(max, plusOne, false, commentTracker);
      }
      final Object minConstant = ExpressionUtils.computeConstantExpression(min);
      if (minConstant instanceof Number) {
        final Number minNumber = (Number)minConstant;
        final int minValue;
        if (plusOne) {
          minValue = minNumber.intValue() - 1;
        }
        else {
          minValue = minNumber.intValue();
        }
        if (minValue == 0) {
          return buildExpressionText(max, false, false, commentTracker);
        }
        if (max instanceof PsiLiteralExpression) {
          final Object maxConstant = ExpressionUtils.computeConstantExpression(max);
          if (maxConstant instanceof Number) {
            final Number number = (Number)maxConstant;
            return String.valueOf(number.intValue() - minValue);
          }
        }
        final String maxText = buildExpressionText(max, false, false, commentTracker);
        if (minValue > 0) {
          return maxText + '-' + minValue;
        }
        else {
          return maxText + '+' + -minValue;
        }
      }
      final int precedence = ParenthesesUtils.getPrecedence(min);
      final String minText;
      if (precedence >= ParenthesesUtils.ADDITIVE_PRECEDENCE) {
        minText = '(' + commentTracker.text(min) + ')';
      }
      else {
        minText = commentTracker.text(min);
      }
      final String maxText = buildExpressionText(max, plusOne, false, commentTracker);
      return maxText + '-' + minText;
    }

    private static String buildExpressionText(PsiExpression expression,
                                              boolean plusOne,
                                              boolean parenthesize,
                                              CommentTracker commentTracker) {
      if (!plusOne) {
        final int precedence = ParenthesesUtils.getPrecedence(expression);
        if (precedence > ParenthesesUtils.ADDITIVE_PRECEDENCE) {
          return '(' + commentTracker.text(expression) + ')';
        }
        else {
          if (parenthesize && precedence >= ParenthesesUtils.ADDITIVE_PRECEDENCE) {
            return '(' + commentTracker.text(expression) + ')';
          }
          return commentTracker.text(expression);
        }
      }
      if (expression instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        if (tokenType == JavaTokenType.MINUS) {
          final PsiExpression rhs = binaryExpression.getROperand();
          if (ExpressionUtils.isOne(rhs)) {
            return commentTracker.text(binaryExpression.getLOperand());
          }
        }
      }
      else if (expression instanceof PsiLiteralExpression) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expression;
        final Object value = literalExpression.getValue();
        if (value instanceof Integer) {
          final Integer integer = (Integer)value;
          return String.valueOf(integer.intValue() + 1);
        }
      }
      final int precedence = ParenthesesUtils.getPrecedence(expression);
      final String result;
      if (precedence > ParenthesesUtils.ADDITIVE_PRECEDENCE) {
        result = '(' + commentTracker.text(expression) + ")+1";
      }
      else {
        result = commentTracker.text(expression) + "+1";
      }
      if (parenthesize) {
        return '(' + result + ')';
      }
      return result;
    }

    @NonNls
    @Nullable
    private static String buildOffsetText(PsiExpression expression,
                                          PsiLocalVariable variable,
                                          PsiExpression limitExpression,
                                          boolean plusOne,
                                          CommentTracker commentTracker) {
      if (expression == null) {
        return null;
      }
      final String expressionText = commentTracker.text(expression);
      final String variableName = variable.getName();
      if (expressionText.equals(variableName)) {
        final PsiExpression initialValue =
          ParenthesesUtils.stripParentheses(limitExpression);
        if (initialValue == null) {
          return null;
        }
        return buildExpressionText(initialValue, plusOne, false, commentTracker);
      }
      else if (expression instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)expression;
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        final String rhsText =
          buildOffsetText(rhs, variable, limitExpression, plusOne, commentTracker);
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if (ExpressionUtils.isZero(lhs)) {
          if (tokenType.equals(JavaTokenType.MINUS)) {
            return '-' + rhsText;
          }
          return rhsText;
        }
        if (plusOne && tokenType.equals(JavaTokenType.MINUS) &&
            ExpressionUtils.isOne(rhs)) {
          return buildOffsetText(lhs, variable, limitExpression,
                                 false, commentTracker);
        }
        final String lhsText = buildOffsetText(lhs, variable,
                                               limitExpression, plusOne, commentTracker);
        if (ExpressionUtils.isZero(rhs)) {
          return lhsText;
        }
        return collapseConstant(lhsText + sign.getText() + rhsText,
                                variable);
      }
      return collapseConstant(commentTracker.text(expression), variable);
    }

    private static String collapseConstant(@NonNls String expressionText, PsiElement context) {
      final Project project = context.getProject();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiExpression fromOffsetExpression =
        factory.createExpressionFromText(expressionText, context);
      final Object fromOffsetConstant =
        ExpressionUtils.computeConstantExpression(
          fromOffsetExpression);
      if (fromOffsetConstant != null) {
        return fromOffsetConstant.toString();
      }
      else {
        return expressionText;
      }
    }
  }

  private static class ManualArrayCopyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(
      @NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      final PsiStatement initialization = statement.getInitialization();
      if (!(initialization instanceof PsiDeclarationStatement)) {
        return;
      }
      final PsiDeclarationStatement declaration =
        (PsiDeclarationStatement)initialization;
      final PsiElement[] declaredElements =
        declaration.getDeclaredElements();
      if (declaredElements.length != 1) {
        return;
      }
      final PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiLocalVariable)) {
        return;
      }
      final PsiLocalVariable variable = (PsiLocalVariable)declaredElement;
      final PsiExpression initialValue = variable.getInitializer();
      if (initialValue == null) {
        return;
      }
      final PsiStatement update = statement.getUpdate();
      final boolean decrement;
      if (VariableAccessUtils.variableIsIncremented(variable, update)) {
        decrement = false;
      }
      else if (VariableAccessUtils.variableIsDecremented(variable,
                                                         update)) {
        decrement = true;
      }
      else {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      if (decrement) {
        if (!ExpressionUtils.isVariableGreaterThanComparison(
          condition, variable)) {
          return;
        }
      }
      else {
        if (!ExpressionUtils.isVariableLessThanComparison(
          condition, variable)) {
          return;
        }
      }
      final PsiStatement body = statement.getBody();
      if (!bodyIsArrayCopy(body, variable, null)) {
        return;
      }
      registerStatementError(statement, Boolean.valueOf(decrement));
    }

    private static boolean bodyIsArrayCopy(
      PsiStatement body, PsiVariable variable,
      @Nullable PsiVariable variable2) {
      if (body instanceof PsiExpressionStatement) {
        final PsiExpressionStatement exp =
          (PsiExpressionStatement)body;
        final PsiExpression expression = exp.getExpression();
        return expressionIsArrayCopy(expression, variable, variable2);
      }
      else if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement =
          (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 1) {
          return bodyIsArrayCopy(statements[0], variable, variable2);
        }
        else if (statements.length == 2) {
          final PsiStatement statement = statements[0];
          if (!(statement instanceof PsiDeclarationStatement)) {
            return false;
          }
          final PsiDeclarationStatement declarationStatement =
            (PsiDeclarationStatement)statement;
          final PsiElement[] declaredElements =
            declarationStatement.getDeclaredElements();
          if (declaredElements.length != 1) {
            return false;
          }
          final PsiElement declaredElement = declaredElements[0];
          if (!(declaredElement instanceof PsiVariable)) {
            return false;
          }
          final PsiVariable localVariable =
            (PsiVariable)declaredElement;
          final PsiExpression initializer =
            localVariable.getInitializer();
          if (!ExpressionUtils.isOffsetArrayAccess(initializer,
                                                   variable)) {
            return false;
          }
          return bodyIsArrayCopy(statements[1], variable,
                                 localVariable);
        }
      }
      return false;
    }

    private static boolean expressionIsArrayCopy(
      @Nullable PsiExpression expression,
      @NotNull PsiVariable variable,
      @Nullable PsiVariable variable2) {
      final PsiExpression strippedExpression =
        ParenthesesUtils.stripParentheses(expression);
      if (strippedExpression == null) {
        return false;
      }
      if (!(strippedExpression instanceof PsiAssignmentExpression)) {
        return false;
      }
      final PsiAssignmentExpression assignment =
        (PsiAssignmentExpression)strippedExpression;
      final IElementType tokenType = assignment.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.EQ)) {
        return false;
      }
      final PsiExpression lhs = assignment.getLExpression();
      if (SideEffectChecker.mayHaveSideEffects(lhs)) {
        return false;
      }
      if (!ExpressionUtils.isOffsetArrayAccess(lhs, variable)) {
        return false;
      }
      final PsiExpression rhs = assignment.getRExpression();
      if (rhs == null) {
        return false;
      }
      if (SideEffectChecker.mayHaveSideEffects(rhs)) {
        return false;
      }
      if (!areExpressionsCopyable(lhs, rhs)) {
        return false;
      }
      final PsiType type = lhs.getType();
      if (type instanceof PsiPrimitiveType) {
        final PsiExpression strippedLhs =
          ParenthesesUtils.stripParentheses(lhs);
        final PsiExpression strippedRhs =
          ParenthesesUtils.stripParentheses(rhs);
        if (!areExpressionsCopyable(strippedLhs, strippedRhs)) {
          return false;
        }
      }
      if (variable2 == null) {
        return ExpressionUtils.isOffsetArrayAccess(rhs, variable);
      }
      else {
        return VariableAccessUtils.evaluatesToVariable(rhs, variable2);
      }
    }

    private static boolean areExpressionsCopyable(
      @Nullable PsiExpression lhs, @Nullable PsiExpression rhs) {
      if (lhs == null || rhs == null) {
        return false;
      }
      final PsiType lhsType = lhs.getType();
      if (lhsType == null) {
        return false;
      }
      final PsiType rhsType = rhs.getType();
      if (rhsType == null) {
        return false;
      }
      if (lhsType instanceof PsiPrimitiveType) {
        if (!lhsType.equals(rhsType)) {
          return false;
        }
      }
      else {
        if (!lhsType.isAssignableFrom(rhsType) ||
            rhsType instanceof PsiPrimitiveType) {
          return false;
        }
      }
      return true;
    }
  }
}