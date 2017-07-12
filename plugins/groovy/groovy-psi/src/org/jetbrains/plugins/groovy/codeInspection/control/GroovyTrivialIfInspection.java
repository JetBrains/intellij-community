/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.control;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.utils.BoolUtils;
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyTrivialIfInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return "Redundant 'if' statement";
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TrivialIfVisitor();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public String buildErrorString(Object... args) {
    return "#ref statement can be simplified #loc";
  }

  @Override
  public GroovyFix buildFix(@NotNull PsiElement location) {
    return new TrivialIfFix();
  }

  private static class TrivialIfFix extends GroovyFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return "Simplify";
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final PsiElement ifKeywordElement = descriptor.getPsiElement();
      final GrIfStatement statement =
          (GrIfStatement) ifKeywordElement.getParent();
      if (isSimplifiableAssignment(statement)) {
        replaceSimplifiableAssignment(statement);
      } else if (isSimplifiableReturn(statement)) {
        repaceSimplifiableReturn(statement);
      } else if (isSimplifiableImplicitReturn(statement)) {
        replaceSimplifiableImplicitReturn(statement);
      } else if (isSimplifiableAssignmentNegated(statement)) {
        replaceSimplifiableAssignmentNegated(statement);
      } else if (isSimplifiableReturnNegated(statement)) {
        repaceSimplifiableReturnNegated(statement);
      } else if (isSimplifiableImplicitReturnNegated(statement)) {
        replaceSimplifiableImplicitReturnNegated(statement);
      } else if (isSimplifiableImplicitAssignment(statement)) {
        replaceSimplifiableImplicitAssignment(statement);
      } else if (isSimplifiableImplicitAssignmentNegated(statement)) {
        replaceSimplifiableImplicitAssignmentNegated(statement);
      }
    }

    private static void replaceSimplifiableImplicitReturn(GrIfStatement statement)
        throws IncorrectOperationException {
      final GrCondition condition = statement.getCondition();
      final String conditionText = condition.getText();
      final PsiElement nextStatement =
        PsiTreeUtil.skipWhitespacesForward(statement);
      @NonNls final String newStatement = "return " + conditionText + ';';
      replaceStatement(statement, newStatement);
      assert nextStatement != null;
      nextStatement.delete();
    }

    private static void repaceSimplifiableReturn(GrIfStatement statement)
        throws IncorrectOperationException {
      final GrCondition condition = statement.getCondition();
      final String conditionText = condition.getText();
      @NonNls final String newStatement = "return " + conditionText + ';';
      replaceStatement(statement, newStatement);
    }

    private static void replaceSimplifiableAssignment(GrIfStatement statement)
        throws IncorrectOperationException {
      final GrCondition condition = statement.getCondition();
      final String conditionText = condition.getText();
      final GrStatement thenBranch = statement.getThenBranch();
      final GrAssignmentExpression assignmentExpression =
          (GrAssignmentExpression) ConditionalUtils.stripBraces(thenBranch);
      final IElementType operator =
          assignmentExpression.getOperationTokenType();
      final String operatorText = getTextForOperator(operator);
      final GrExpression lhs = assignmentExpression.getLValue();
      final String lhsText = lhs.getText();
      replaceStatement(statement,
          lhsText + operatorText + conditionText + ';');
    }

    private static void replaceSimplifiableImplicitAssignment(GrIfStatement statement)
        throws IncorrectOperationException {
      final PsiElement prevStatement =
        PsiTreeUtil.skipWhitespacesBackward(statement);
      if (prevStatement == null) {
        return;
      }
      final GrCondition condition = statement.getCondition();
      final String conditionText = condition.getText();
      final GrStatement thenBranch = statement.getThenBranch();
      final GrAssignmentExpression assignmentExpression =
          (GrAssignmentExpression) ConditionalUtils.stripBraces(thenBranch);
      final IElementType operator =
          assignmentExpression.getOperationTokenType();
      final GrExpression lhs = assignmentExpression.getLValue();
      final String lhsText = lhs.getText();
      replaceStatement(statement,
          lhsText + operator + conditionText + ';');
      prevStatement.delete();
    }

    private static void replaceSimplifiableImplicitAssignmentNegated(GrIfStatement statement)
        throws IncorrectOperationException {
      final PsiElement prevStatement =
        PsiTreeUtil.skipWhitespacesBackward(statement);
      final GrCondition condition = statement.getCondition();
      if (!(condition instanceof GrExpression)) {
        return;
      }
      final GrExpression expression = (GrExpression) condition;
      final String conditionText =
          BoolUtils.getNegatedExpressionText(expression);
      final GrStatement thenBranch = statement.getThenBranch();
      final GrAssignmentExpression assignmentExpression =
          (GrAssignmentExpression) ConditionalUtils.stripBraces(thenBranch);
      final IElementType operator =
          assignmentExpression.getOperationTokenType();
      final String operatorText = getTextForOperator(operator);
      final GrExpression lhs = assignmentExpression.getLValue();
      final String lhsText = lhs.getText();
      replaceStatement(statement,
          lhsText + operatorText + conditionText + ';');
      assert prevStatement != null;
      prevStatement.delete();
    }

    private static void replaceSimplifiableImplicitReturnNegated(GrIfStatement statement)
        throws IncorrectOperationException {
      final GrCondition condition = statement.getCondition();
      if (!(condition instanceof GrExpression)) {
        return;
      }
      final GrExpression expression = (GrExpression) condition;
      final String conditionText =
          BoolUtils.getNegatedExpressionText(expression);
      final PsiElement nextStatement =
        PsiTreeUtil.skipWhitespacesForward(statement);
      if (nextStatement == null) {
        return;
      }
      @NonNls final String newStatement = "return " + conditionText + ';';
      replaceStatement(statement, newStatement);
      nextStatement.delete();
    }

    private static void repaceSimplifiableReturnNegated(GrIfStatement statement)
        throws IncorrectOperationException {
      final GrCondition condition = statement.getCondition();
      if (!(condition instanceof GrExpression)) {
        return;
      }
      final GrExpression expression = (GrExpression) condition;
      final String conditionText =
          BoolUtils.getNegatedExpressionText(expression);
      @NonNls final String newStatement = "return " + conditionText + ';';
      replaceStatement(statement, newStatement);
    }

    private static void replaceSimplifiableAssignmentNegated(GrIfStatement statement)
        throws IncorrectOperationException {
      final GrCondition condition = statement.getCondition();
      if (!(condition instanceof GrExpression)) {
        return;
      }
      final GrExpression expression = (GrExpression) condition;
      final String conditionText =
          BoolUtils.getNegatedExpressionText(expression);
      final GrStatement thenBranch = statement.getThenBranch();
      final GrAssignmentExpression assignmentExpression =
          (GrAssignmentExpression) ConditionalUtils.stripBraces(thenBranch);
      final IElementType operator =
          assignmentExpression.getOperationTokenType();
      final String operatorText = getTextForOperator(operator);
      final GrExpression lhs = assignmentExpression.getLValue();
      final String lhsText = lhs.getText();
      replaceStatement(statement,
          lhsText + operatorText + conditionText + ';');
    }
  }

  private static class TrivialIfVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull GrIfStatement ifStatement) {
      super.visitIfStatement(ifStatement);
      final GrCondition condition = ifStatement.getCondition();
      if (!(condition instanceof GrExpression)) {
        return;
      }
      final PsiType type = ((GrExpression)condition).getType();
      if (type == null || !(PsiType.BOOLEAN.isAssignableFrom(type))) {
        return;
      }

      if (isSimplifiableAssignment(ifStatement)) {
        registerStatementError(ifStatement);
        return;
      }

      if (isSimplifiableReturn(ifStatement)) {
        registerStatementError(ifStatement);
        return;
      }

      if (isSimplifiableImplicitReturn(ifStatement)) {
        registerStatementError(ifStatement);
        return;
      }
      if (isSimplifiableAssignmentNegated(ifStatement)) {
        registerStatementError(ifStatement);
        return;
      }

      if (isSimplifiableReturnNegated(ifStatement)) {
        registerStatementError(ifStatement);
        return;
      }

      if (isSimplifiableImplicitReturnNegated(ifStatement)) {
        registerStatementError(ifStatement);
        return;
      }
      if (isSimplifiableImplicitAssignment(ifStatement)) {
        registerStatementError(ifStatement);
        return;
      }

      if (isSimplifiableImplicitAssignmentNegated(ifStatement)) {
        registerStatementError(ifStatement);
      }
    }
  }

  public static boolean isSimplifiableImplicitReturn(GrIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    GrStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    final PsiElement nextStatement =
      PsiTreeUtil.skipWhitespacesForward(ifStatement);
    if (!(nextStatement instanceof GrStatement)) {
      return false;
    }

    final GrStatement elseBranch = (GrStatement) nextStatement;
    return ConditionalUtils.isReturn(thenBranch, "true")
        && ConditionalUtils.isReturn(elseBranch, "false");
  }

  public static boolean isSimplifiableImplicitReturnNegated(GrIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    GrStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);

    final PsiElement nextStatement =
      PsiTreeUtil.skipWhitespacesForward(ifStatement);
    if (!(nextStatement instanceof GrStatement)) {
      return false;
    }
    final GrStatement elseBranch = (GrStatement) nextStatement;
    return ConditionalUtils.isReturn(thenBranch, "false")
        && ConditionalUtils.isReturn(elseBranch, "true");
  }

  public static boolean isSimplifiableReturn(GrIfStatement ifStatement) {
    GrStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    GrStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ConditionalUtils.stripBraces(elseBranch);
    return ConditionalUtils.isReturn(thenBranch, "true")
        && ConditionalUtils.isReturn(elseBranch, "false");
  }

  public static boolean isSimplifiableReturnNegated(GrIfStatement ifStatement) {
    GrStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    GrStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ConditionalUtils.stripBraces(elseBranch);
    return ConditionalUtils.isReturn(thenBranch, "false")
        && ConditionalUtils.isReturn(elseBranch, "true");
  }

  public static boolean isSimplifiableAssignment(GrIfStatement ifStatement) {
    GrStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    GrStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ConditionalUtils.stripBraces(elseBranch);
    if (ConditionalUtils.isAssignment(thenBranch, "true") &&
        ConditionalUtils.isAssignment(elseBranch, "false")) {
      final GrAssignmentExpression thenExpression =
          (GrAssignmentExpression) thenBranch;
      final GrAssignmentExpression elseExpression =
          (GrAssignmentExpression) elseBranch;
      final IElementType thenSign = thenExpression.getOperationTokenType();
      final IElementType elseSign = elseExpression.getOperationTokenType();
      if (!thenSign.equals(elseSign)) {
        return false;
      }
      final GrExpression thenLhs = thenExpression.getLValue();
      final GrExpression elseLhs = elseExpression.getLValue();
      return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
          elseLhs);
    } else {
      return false;
    }
  }

  public static boolean isSimplifiableAssignmentNegated(GrIfStatement ifStatement) {
    GrStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    GrStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ConditionalUtils.stripBraces(elseBranch);
    if (ConditionalUtils.isAssignment(thenBranch, "false") &&
        ConditionalUtils.isAssignment(elseBranch, "true")) {
      final GrAssignmentExpression thenExpression =
          (GrAssignmentExpression) thenBranch;
      final GrAssignmentExpression elseExpression =
          (GrAssignmentExpression) elseBranch;
      final IElementType thenSign = thenExpression.getOperationTokenType();
      final IElementType elseSign = elseExpression.getOperationTokenType();
      if (!thenSign.equals(elseSign)) {
        return false;
      }
      final GrExpression thenLhs = thenExpression.getLValue();
      final GrExpression elseLhs = elseExpression.getLValue();
      return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
          elseLhs);
    } else {
      return false;
    }
  }

  public static boolean isSimplifiableImplicitAssignment(GrIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    GrStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    final PsiElement nextStatement =
      PsiTreeUtil.skipWhitespacesBackward(ifStatement);
    if (!(nextStatement instanceof GrStatement)) {
      return false;
    }
    GrStatement elseBranch = (GrStatement) nextStatement;

    elseBranch = ConditionalUtils.stripBraces(elseBranch);
    if (ConditionalUtils.isAssignment(thenBranch, "true") &&
        ConditionalUtils.isAssignment(elseBranch, "false")) {
      final GrAssignmentExpression thenExpression =
          (GrAssignmentExpression) thenBranch;
      final GrAssignmentExpression elseExpression =
          (GrAssignmentExpression) elseBranch;
      final IElementType thenSign = thenExpression.getOperationTokenType();
      final IElementType elseSign = elseExpression.getOperationTokenType();
      if (!thenSign.equals(elseSign)) {
        return false;
      }
      final GrExpression thenLhs = thenExpression.getLValue();
      final GrExpression elseLhs = elseExpression.getLValue();
      return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
          elseLhs);
    } else {
      return false;
    }
  }

  public static boolean isSimplifiableImplicitAssignmentNegated(GrIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    GrStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    final PsiElement nextStatement =
      PsiTreeUtil.skipWhitespacesBackward(ifStatement);
    if (!(nextStatement instanceof GrStatement)) {
      return false;
    }
    GrStatement elseBranch = (GrStatement) nextStatement;

    elseBranch = ConditionalUtils.stripBraces(elseBranch);
    if (ConditionalUtils.isAssignment(thenBranch, "false") &&
        ConditionalUtils.isAssignment(elseBranch, "true")) {
      final GrAssignmentExpression thenExpression =
          (GrAssignmentExpression) thenBranch;
      final GrAssignmentExpression elseExpression =
          (GrAssignmentExpression) elseBranch;
      final IElementType thenSign = thenExpression.getOperationTokenType();
      final IElementType elseSign = elseExpression.getOperationTokenType();
      if (!thenSign.equals(elseSign)) {
        return false;
      }
      final GrExpression thenLhs = thenExpression.getLValue();
      final GrExpression elseLhs = elseExpression.getLValue();
      return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
          elseLhs);
    } else {
      return false;
    }
  }

  @NonNls
  private static String getTextForOperator(IElementType operator) {
    if (operator.equals(GroovyTokenTypes.mASSIGN)) {
      return "=";
    }
    if (operator.equals(GroovyTokenTypes.mNOT_EQUAL)) {
      return "!=";
    }
    if (operator.equals(GroovyTokenTypes.mLE)) {
      return "<=";
    }
    if (operator.equals(GroovyTokenTypes.mGE)) {
      return ">=";
    }
    if (operator.equals(GroovyTokenTypes.mLT)) {
      return "<=";
    }
    if (operator.equals(GroovyTokenTypes.mGT)) {
      return ">=";
    }
    if (operator.equals(GroovyTokenTypes.mELVIS)) {
      return "==";
    }
    if (operator.equals(GroovyTokenTypes.mPLUS_ASSIGN)) {
      return "+=";
    }
    if (operator.equals(GroovyTokenTypes.mMINUS_ASSIGN)) {
      return "-=";
    }
    if (operator.equals(GroovyTokenTypes.mSTAR_ASSIGN)) {
      return "*=";
    }
    if (operator.equals(GroovyTokenTypes.mDIV_ASSIGN)) {
      return "/=";
    }
    if (operator.equals(GroovyTokenTypes.mMOD_ASSIGN)) {
      return "%=";
    }
    if (operator.equals(GroovyTokenTypes.mBXOR_ASSIGN)) {
      return "^=";
    }
    if (operator.equals(GroovyTokenTypes.mBAND_ASSIGN)) {
      return "&=";
    }
    if (operator.equals(GroovyTokenTypes.mBOR_ASSIGN)) {
      return "|=";
    }
    return "unknown";
  }
}
