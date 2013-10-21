/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class TrivialIfInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "RedundantIfStatement";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("trivial.if.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("trivial.if.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new TrivialIfFix();
  }

  private static class TrivialIfFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }
    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement ifKeywordElement = descriptor.getPsiElement();
      final PsiIfStatement statement =
        (PsiIfStatement)ifKeywordElement.getParent();
      if (isSimplifiableAssignment(statement)) {
        replaceSimplifiableAssignment(statement);
      }
      else if (isSimplifiableReturn(statement)) {
        repaceSimplifiableReturn(statement);
      }
      else if (isSimplifiableImplicitReturn(statement)) {
        replaceSimplifiableImplicitReturn(statement);
      }
      else if (isSimplifiableAssignmentNegated(statement)) {
        replaceSimplifiableAssignmentNegated(statement);
      }
      else if (isSimplifiableReturnNegated(statement)) {
        repaceSimplifiableReturnNegated(statement);
      }
      else if (isSimplifiableImplicitReturnNegated(statement)) {
        replaceSimplifiableImplicitReturnNegated(statement);
      }
      else if (isSimplifiableImplicitAssignment(statement)) {
        replaceSimplifiableImplicitAssignment(statement);
      }
      else if (isSimplifiableImplicitAssignmentNegated(statement)) {
        replaceSimplifiableImplicitAssignmentNegated(statement);
      }
    }

    private static void replaceSimplifiableImplicitReturn(
      PsiIfStatement statement) throws IncorrectOperationException {
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      final String conditionText = condition.getText();
      final PsiElement nextStatement =
        PsiTreeUtil.skipSiblingsForward(statement,
                                        PsiWhiteSpace.class);
      @NonNls final String newStatement = "return " + conditionText + ';';
      replaceStatement(statement, newStatement);
      assert nextStatement != null;
      deleteElement(nextStatement);
    }

    private static void repaceSimplifiableReturn(PsiIfStatement statement)
      throws IncorrectOperationException {
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      final String conditionText = condition.getText();
      @NonNls final String newStatement = "return " + conditionText + ';';
      final Collection<PsiComment> comments = PsiTreeUtil.findChildrenOfType(statement, PsiComment.class);
      final PsiElement parent = statement.getParent();
      for (PsiComment comment : comments) {
        parent.addBefore(comment.copy(), statement);
      }
      replaceStatement(statement, newStatement);
    }

    private static void replaceSimplifiableAssignment(
      PsiIfStatement statement)
      throws IncorrectOperationException {
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      final String conditionText = condition.getText();
      final PsiStatement thenBranch = statement.getThenBranch();
      final PsiExpressionStatement assignmentStatement =
        (PsiExpressionStatement)
          ControlFlowUtils.stripBraces(thenBranch);
      final PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)assignmentStatement.getExpression();
      final PsiJavaToken operator =
        assignmentExpression.getOperationSign();
      final String operand = operator.getText();
      final PsiExpression lhs = assignmentExpression.getLExpression();
      final String lhsText = lhs.getText();
      replaceStatement(statement,
                       lhsText + operand + conditionText + ';');
    }

    private static void replaceSimplifiableImplicitAssignment(
      PsiIfStatement statement) throws IncorrectOperationException {
      final PsiElement prevStatement =
        PsiTreeUtil.skipSiblingsBackward(statement,
                                         PsiWhiteSpace.class);
      if (prevStatement == null) {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      final String conditionText = condition.getText();
      final PsiStatement thenBranch = statement.getThenBranch();
      final PsiExpressionStatement assignmentStatement =
        (PsiExpressionStatement)
          ControlFlowUtils.stripBraces(thenBranch);
      final PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)assignmentStatement.getExpression();
      final PsiJavaToken operator =
        assignmentExpression.getOperationSign();
      final String operand = operator.getText();
      final PsiExpression lhs = assignmentExpression.getLExpression();
      final String lhsText = lhs.getText();
      replaceStatement(statement,
                       lhsText + operand + conditionText + ';');
      deleteElement(prevStatement);
    }

    private static void replaceSimplifiableImplicitAssignmentNegated(
      PsiIfStatement statement)
      throws IncorrectOperationException {
      final PsiElement prevStatement =
        PsiTreeUtil.skipSiblingsBackward(statement,
                                         PsiWhiteSpace.class);
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      final String conditionText =
        BoolUtils.getNegatedExpressionText(condition);
      final PsiStatement thenBranch = statement.getThenBranch();
      final PsiExpressionStatement assignmentStatement =
        (PsiExpressionStatement)
          ControlFlowUtils.stripBraces(thenBranch);
      final PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)
          assignmentStatement.getExpression();
      final PsiJavaToken operator =
        assignmentExpression.getOperationSign();
      final String operand = operator.getText();
      final PsiExpression lhs = assignmentExpression.getLExpression();
      final String lhsText = lhs.getText();
      replaceStatement(statement,
                       lhsText + operand + conditionText + ';');
      assert prevStatement != null;
      deleteElement(prevStatement);
    }

    private static void replaceSimplifiableImplicitReturnNegated(
      PsiIfStatement statement)
      throws IncorrectOperationException {
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      final String conditionText =
        BoolUtils.getNegatedExpressionText(condition);
      final PsiElement nextStatement =
        PsiTreeUtil.skipSiblingsForward(statement,
                                        PsiWhiteSpace.class);
      if (nextStatement == null) {
        return;
      }
      @NonNls final String newStatement = "return " + conditionText + ';';
      replaceStatement(statement, newStatement);
      deleteElement(nextStatement);
    }

    private static void repaceSimplifiableReturnNegated(
      PsiIfStatement statement)
      throws IncorrectOperationException {
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      final String conditionText =
        BoolUtils.getNegatedExpressionText(condition);
      @NonNls final String newStatement = "return " + conditionText + ';';
      replaceStatement(statement, newStatement);
    }

    private static void replaceSimplifiableAssignmentNegated(
      PsiIfStatement statement)
      throws IncorrectOperationException {
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      final String conditionText =
        BoolUtils.getNegatedExpressionText(condition);
      final PsiStatement thenBranch = statement.getThenBranch();
      final PsiExpressionStatement assignmentStatement =
        (PsiExpressionStatement)
          ControlFlowUtils.stripBraces(thenBranch);
      final PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)
          assignmentStatement.getExpression();
      final PsiJavaToken operator =
        assignmentExpression.getOperationSign();
      final String operand = operator.getText();
      final PsiExpression lhs = assignmentExpression.getLExpression();
      final String lhsText = lhs.getText();
      replaceStatement(statement,
                       lhsText + operand + conditionText + ';');
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TrivialIfVisitor();
  }

  private static class TrivialIfVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement ifStatement) {
      super.visitIfStatement(ifStatement);
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null) {
        return;
      }
      if (PsiUtilCore.hasErrorElementChild(ifStatement)) {
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

  public static boolean isSimplifiableImplicitReturn(
    PsiIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);
    final PsiElement nextStatement =
      PsiTreeUtil.skipSiblingsForward(ifStatement,
                                      PsiWhiteSpace.class);
    if (!(nextStatement instanceof PsiStatement)) {
      return false;
    }

    final PsiStatement elseBranch = (PsiStatement)nextStatement;
    return ConditionalUtils.isReturn(thenBranch, PsiKeyword.TRUE)
           && ConditionalUtils.isReturn(elseBranch, PsiKeyword.FALSE);
  }

  public static boolean isSimplifiableImplicitReturnNegated(
    PsiIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);

    final PsiElement nextStatement =
      PsiTreeUtil.skipSiblingsForward(ifStatement,
                                      PsiWhiteSpace.class);
    if (!(nextStatement instanceof PsiStatement)) {
      return false;
    }
    final PsiStatement elseBranch = (PsiStatement)nextStatement;
    return ConditionalUtils.isReturn(thenBranch, PsiKeyword.FALSE)
           && ConditionalUtils.isReturn(elseBranch, PsiKeyword.TRUE);
  }

  public static boolean isSimplifiableReturn(PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ControlFlowUtils.stripBraces(elseBranch);
    return ConditionalUtils.isReturn(thenBranch, PsiKeyword.TRUE)
           && ConditionalUtils.isReturn(elseBranch, PsiKeyword.FALSE);
  }

  public static boolean isSimplifiableReturnNegated(
    PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ControlFlowUtils.stripBraces(elseBranch);
    return ConditionalUtils.isReturn(thenBranch, PsiKeyword.FALSE)
           && ConditionalUtils.isReturn(elseBranch, PsiKeyword.TRUE);
  }

  public static boolean isSimplifiableAssignment(PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ControlFlowUtils.stripBraces(elseBranch);
    if (ConditionalUtils.isAssignment(thenBranch, PsiKeyword.TRUE) &&
        ConditionalUtils.isAssignment(elseBranch, PsiKeyword.FALSE)) {
      final PsiExpressionStatement thenExpressionStatement =
        (PsiExpressionStatement)thenBranch;
      final PsiAssignmentExpression thenExpression =
        (PsiAssignmentExpression)
          thenExpressionStatement.getExpression();
      final PsiExpressionStatement elseExpressionStatement =
        (PsiExpressionStatement)elseBranch;
      final PsiAssignmentExpression elseExpression =
        (PsiAssignmentExpression)
          elseExpressionStatement.getExpression();
      final IElementType thenTokenType = thenExpression.getOperationTokenType();
      if (!thenTokenType.equals(elseExpression.getOperationTokenType())) {
        return false;
      }
      final PsiExpression thenLhs = thenExpression.getLExpression();
      final PsiExpression elseLhs = elseExpression.getLExpression();
      return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                         elseLhs);
    }
    else {
      return false;
    }
  }

  public static boolean isSimplifiableAssignmentNegated(
    PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ControlFlowUtils.stripBraces(elseBranch);
    if (ConditionalUtils.isAssignment(thenBranch, PsiKeyword.FALSE) &&
        ConditionalUtils.isAssignment(elseBranch, PsiKeyword.TRUE)) {
      final PsiExpressionStatement thenExpressionStatement =
        (PsiExpressionStatement)thenBranch;
      final PsiAssignmentExpression thenExpression =
        (PsiAssignmentExpression)
          thenExpressionStatement.getExpression();
      final PsiExpressionStatement elseExpressionStatement =
        (PsiExpressionStatement)elseBranch;
      final PsiAssignmentExpression elseExpression =
        (PsiAssignmentExpression)
          elseExpressionStatement.getExpression();
      final IElementType thenTokenType = thenExpression.getOperationTokenType();
      if (!thenTokenType.equals(elseExpression.getOperationTokenType())) {
        return false;
      }
      final PsiExpression thenLhs = thenExpression.getLExpression();
      final PsiExpression elseLhs = elseExpression.getLExpression();
      return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                         elseLhs);
    }
    else {
      return false;
    }
  }

  public static boolean isSimplifiableImplicitAssignment(
    PsiIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);
    final PsiElement nextStatement =
      PsiTreeUtil.skipSiblingsBackward(ifStatement,
                                       PsiWhiteSpace.class);
    if (!(nextStatement instanceof PsiStatement)) {
      return false;
    }
    PsiStatement elseBranch = (PsiStatement)nextStatement;
    elseBranch = ControlFlowUtils.stripBraces(elseBranch);
    if (ConditionalUtils.isAssignment(thenBranch, PsiKeyword.TRUE) &&
        ConditionalUtils.isAssignment(elseBranch, PsiKeyword.FALSE)) {
      final PsiExpressionStatement thenExpressionStatement =
        (PsiExpressionStatement)thenBranch;
      final PsiAssignmentExpression thenExpression =
        (PsiAssignmentExpression)
          thenExpressionStatement.getExpression();
      final PsiExpressionStatement elseExpressionStatement =
        (PsiExpressionStatement)elseBranch;
      final PsiAssignmentExpression elseExpression =
        (PsiAssignmentExpression)
          elseExpressionStatement.getExpression();
      final IElementType thenTokenType = thenExpression.getOperationTokenType();
      if (!thenTokenType.equals(elseExpression.getOperationTokenType())) {
        return false;
      }
      final PsiExpression thenLhs = thenExpression.getLExpression();
      final PsiExpression elseLhs = elseExpression.getLExpression();
      return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                         elseLhs);
    }
    else {
      return false;
    }
  }

  public static boolean isSimplifiableImplicitAssignmentNegated(
    PsiIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);
    final PsiElement nextStatement =
      PsiTreeUtil.skipSiblingsBackward(ifStatement,
                                       PsiWhiteSpace.class);
    if (!(nextStatement instanceof PsiStatement)) {
      return false;
    }
    PsiStatement elseBranch = (PsiStatement)nextStatement;
    elseBranch = ControlFlowUtils.stripBraces(elseBranch);
    if (ConditionalUtils.isAssignment(thenBranch, PsiKeyword.FALSE) &&
        ConditionalUtils.isAssignment(elseBranch, PsiKeyword.TRUE)) {
      final PsiExpressionStatement thenExpressionStatement =
        (PsiExpressionStatement)thenBranch;
      final PsiAssignmentExpression thenExpression =
        (PsiAssignmentExpression)
          thenExpressionStatement.getExpression();
      final PsiExpressionStatement elseExpressionStatement =
        (PsiExpressionStatement)elseBranch;
      final PsiAssignmentExpression elseExpression =
        (PsiAssignmentExpression)
          elseExpressionStatement.getExpression();
      final IElementType thenTokenType = thenExpression.getOperationTokenType();
      if (!thenTokenType.equals(elseExpression.getOperationTokenType())) {
        return false;
      }
      final PsiExpression thenLhs = thenExpression.getLExpression();
      final PsiExpression elseLhs = elseExpression.getLExpression();
      return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                         elseLhs);
    }
    else {
      return false;
    }
  }
}