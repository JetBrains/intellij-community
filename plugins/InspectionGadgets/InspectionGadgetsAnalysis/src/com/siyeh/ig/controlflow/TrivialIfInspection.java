/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class TrivialIfInspection extends BaseInspection implements CleanupLocalInspectionTool {

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
      return InspectionGadgetsBundle.message(
        "constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement ifKeywordElement = descriptor.getPsiElement();
      final PsiIfStatement statement = (PsiIfStatement)ifKeywordElement.getParent();
      simplify(statement);
    }
  }

  public static void simplify(PsiIfStatement statement) {
    if (isSimplifiableAssignment(statement)) {
      replaceSimplifiableAssignment(statement);
    }
    else if (isSimplifiableReturn(statement)) {
      replaceSimplifiableReturn(statement);
    }
    else if (isSimplifiableImplicitReturn(statement)) {
      replaceSimplifiableImplicitReturn(statement);
    }
    else if (isSimplifiableAssignmentNegated(statement)) {
      replaceSimplifiableAssignmentNegated(statement);
    }
    else if (isSimplifiableReturnNegated(statement)) {
      replaceSimplifiableReturnNegated(statement);
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
    else if (isSimplifiableAssert(statement)) {
      replaceSimplifiableAssert(statement);
    }
  }

  private static void replaceSimplifiableAssert(PsiIfStatement statement) {
    final PsiExpression condition = statement.getCondition();
    if (condition == null) {
      return;
    }
    final String conditionText = BoolUtils.getNegatedExpressionText(condition);
    if (statement.getElseBranch() != null) {
      return;
    }
    final PsiStatement thenBranch = ControlFlowUtils.stripBraces(statement.getThenBranch());
    if (!(thenBranch instanceof PsiAssertStatement)) {
      return;
    }
    final PsiAssertStatement assertStatement = (PsiAssertStatement)thenBranch;
    final PsiExpression assertCondition = assertStatement.getAssertCondition();
    if (assertCondition == null) {
      return;
    }
    final PsiExpression replacementCondition = JavaPsiFacade.getElementFactory(statement.getProject()).createExpressionFromText(
      BoolUtils.isFalse(assertCondition) ? conditionText : conditionText + "||" + assertCondition.getText(), statement);
    assertCondition.replace(replacementCondition);
    statement.replace(assertStatement);
  }

  private static void replaceSimplifiableImplicitReturn(PsiIfStatement statement) {
    final PsiExpression condition = statement.getCondition();
    if (condition == null) {
      return;
    }
    final String conditionText = condition.getText();
    final PsiElement nextStatement = PsiTreeUtil.skipWhitespacesForward(statement);
    @NonNls final String newStatement = "return " + conditionText + ';';
    PsiReplacementUtil.replaceStatement(statement, newStatement);
    assert nextStatement != null;
    nextStatement.delete();
  }

  private static void replaceSimplifiableReturn(PsiIfStatement statement) {
    final PsiExpression condition = statement.getCondition();
    if (condition == null) {
      return;
    }
    final Collection<PsiComment> comments = PsiTreeUtil.findChildrenOfType(statement, PsiComment.class);
    final PsiElement parent = statement.getParent();
    for (PsiComment comment : comments) {
      parent.addBefore(comment.copy(), statement);
    }
    final String conditionText = condition.getText();
    final @NonNls String newStatement = "return " + conditionText + ';';
    PsiReplacementUtil.replaceStatement(statement, newStatement);
  }

  private static void replaceSimplifiableAssignment(PsiIfStatement statement) {
    final PsiExpression condition = statement.getCondition();
    if (condition == null) {
      return;
    }
    final Collection<PsiComment> comments = ContainerUtil.map(PsiTreeUtil.findChildrenOfType(statement, PsiComment.class),
                                                              comment -> (PsiComment)comment.copy());
    final String conditionText = condition.getText();
    final PsiStatement thenBranch = statement.getThenBranch();
    final PsiExpressionStatement assignmentStatement = (PsiExpressionStatement)ControlFlowUtils.stripBraces(thenBranch);
    if (assignmentStatement == null) {
      return;
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)assignmentStatement.getExpression();
    final PsiJavaToken operator = assignmentExpression.getOperationSign();
    final String operand = operator.getText();
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final String lhsText = lhs.getText();
    final PsiElement parent = statement.getParent();
    for (PsiComment comment : comments) {
      parent.addBefore(comment, statement);
    }
    PsiReplacementUtil.replaceStatement(statement, lhsText + operand + conditionText + ';');
  }

  private static void replaceSimplifiableImplicitAssignment(PsiIfStatement statement) {
    final PsiElement prevStatement = PsiTreeUtil.skipWhitespacesBackward(statement);
    if (prevStatement == null) {
      return;
    }
    final PsiExpression condition = statement.getCondition();
    if (condition == null) {
      return;
    }
    final String conditionText = condition.getText();
    final PsiStatement thenBranch = statement.getThenBranch();
    final PsiExpressionStatement assignmentStatement = (PsiExpressionStatement)ControlFlowUtils.stripBraces(thenBranch);
    if (assignmentStatement == null) {
      return;
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)assignmentStatement.getExpression();
    final PsiJavaToken operator = assignmentExpression.getOperationSign();
    final String operand = operator.getText();
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final String lhsText = lhs.getText();
    PsiReplacementUtil.replaceStatement(statement, lhsText + operand + conditionText + ';');
    prevStatement.delete();
  }

  private static void replaceSimplifiableImplicitAssignmentNegated(PsiIfStatement statement) {
    final PsiElement prevStatement = PsiTreeUtil.skipWhitespacesBackward(statement);
    final PsiExpression condition = statement.getCondition();
    if (condition == null) {
      return;
    }
    final String conditionText = BoolUtils.getNegatedExpressionText(condition);
    final PsiStatement thenBranch = statement.getThenBranch();
    final PsiExpressionStatement assignmentStatement = (PsiExpressionStatement)ControlFlowUtils.stripBraces(thenBranch);
    if (assignmentStatement == null) {
      return;
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) assignmentStatement.getExpression();
    final PsiJavaToken operator = assignmentExpression.getOperationSign();
    final String operand = operator.getText();
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final String lhsText = lhs.getText();
    PsiReplacementUtil.replaceStatement(statement, lhsText + operand + conditionText + ';');
    assert prevStatement != null;
    prevStatement.delete();
  }

  private static void replaceSimplifiableImplicitReturnNegated(PsiIfStatement statement) {
    final PsiExpression condition = statement.getCondition();
    if (condition == null) {
      return;
    }
    final String conditionText = BoolUtils.getNegatedExpressionText(condition);
    final PsiElement nextStatement = PsiTreeUtil.skipWhitespacesForward(statement);
    if (nextStatement == null) {
      return;
    }
    final PsiElement nextSibling = statement.getNextSibling();
    if (nextSibling != nextStatement) {
      statement.getParent().deleteChildRange(nextSibling, nextStatement.getPrevSibling());
    }
    @NonNls final String newStatement = "return " + conditionText + ';';
    PsiReplacementUtil.replaceStatement(statement, newStatement);
    nextStatement.delete();
  }

  private static void replaceSimplifiableReturnNegated(PsiIfStatement statement) {
    final PsiExpression condition = statement.getCondition();
    if (condition == null) {
      return;
    }
    final String conditionText = BoolUtils.getNegatedExpressionText(condition);
    @NonNls final String newStatement = "return " + conditionText + ';';
    PsiReplacementUtil.replaceStatement(statement, newStatement);
  }

  private static void replaceSimplifiableAssignmentNegated(PsiIfStatement statement) {
    final PsiExpression condition = statement.getCondition();
    if (condition == null) {
      return;
    }
    final String conditionText = BoolUtils.getNegatedExpressionText(condition);
    final PsiStatement thenBranch = statement.getThenBranch();
    final PsiExpressionStatement assignmentStatement = (PsiExpressionStatement)ControlFlowUtils.stripBraces(thenBranch);
    if (assignmentStatement == null) {
      return;
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)assignmentStatement.getExpression();
    final PsiJavaToken operator = assignmentExpression.getOperationSign();
    final String operand = operator.getText();
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final String lhsText = lhs.getText();
    PsiReplacementUtil.replaceStatement(statement, lhsText + operand + conditionText + ';');
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
      if (isTrivial(ifStatement)) {
        registerStatementError(ifStatement);
      }
    }
  }

  public static boolean isTrivial(PsiIfStatement ifStatement) {
    if (PsiUtilCore.hasErrorElementChild(ifStatement)) {
      return false;
    }
    return isSimplifiableAssignment(ifStatement) ||
           isSimplifiableReturn(ifStatement) ||
           isSimplifiableImplicitReturn(ifStatement) ||
           isSimplifiableAssignmentNegated(ifStatement) ||
           isSimplifiableReturnNegated(ifStatement) ||
           isSimplifiableImplicitReturnNegated(ifStatement) ||
           isSimplifiableImplicitAssignment(ifStatement) ||
           isSimplifiableImplicitAssignmentNegated(ifStatement) ||
           isSimplifiableAssert(ifStatement);
  }

  private static boolean isSimplifiableImplicitReturn(PsiIfStatement ifStatement) {
    return isSimplifiableImplicitReturn(ifStatement, PsiKeyword.TRUE, PsiKeyword.FALSE);
  }

  private static boolean isSimplifiableImplicitReturnNegated(PsiIfStatement ifStatement) {
    return isSimplifiableImplicitReturn(ifStatement, PsiKeyword.FALSE, PsiKeyword.TRUE);
  }

  private static boolean isSimplifiableImplicitReturn(PsiIfStatement ifStatement, String thenReturn, String elseReturn) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);
    final PsiElement nextStatement = PsiTreeUtil.skipWhitespacesForward(ifStatement);
    if (!(nextStatement instanceof PsiStatement)) {
      return false;
    }
    final PsiStatement elseBranch = (PsiStatement)nextStatement;
    return isReturn(thenBranch, thenReturn) && isReturn(elseBranch, elseReturn);
  }

  private static boolean isSimplifiableReturn(PsiIfStatement ifStatement) {
    return isSimplifiableReturn(ifStatement, PsiKeyword.TRUE, PsiKeyword.FALSE);
  }

  private static boolean isSimplifiableReturnNegated(PsiIfStatement ifStatement) {
    return isSimplifiableReturn(ifStatement, PsiKeyword.FALSE, PsiKeyword.TRUE);
  }

  private static boolean isSimplifiableReturn(PsiIfStatement ifStatement, String thenReturn, String elseReturn) {
    final PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    final PsiStatement elseBranch = ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
    return isReturn(thenBranch, thenReturn) && isReturn(elseBranch, elseReturn);
  }

  private static boolean isSimplifiableAssignment(PsiIfStatement ifStatement) {
    return isSimplifiableAssignment(ifStatement, PsiKeyword.TRUE, PsiKeyword.FALSE);
  }

  private static boolean isSimplifiableAssignmentNegated(PsiIfStatement ifStatement) {
    return isSimplifiableAssignment(ifStatement, PsiKeyword.FALSE, PsiKeyword.TRUE);
  }

  private static boolean isSimplifiableAssignment(PsiIfStatement ifStatement, String thenAssignment, String elseAssignment) {
    final PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    final PsiStatement elseBranch = ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
    return isSimplifiableAssignment(thenBranch, elseBranch, thenAssignment, elseAssignment);
  }

  private static boolean isSimplifiableImplicitAssignment(PsiIfStatement ifStatement) {
    return isSimplifiableImplicitAssignment(ifStatement, PsiKeyword.TRUE, PsiKeyword.FALSE);
  }

  private static boolean isSimplifiableImplicitAssignmentNegated(PsiIfStatement ifStatement) {
    return isSimplifiableImplicitAssignment(ifStatement, PsiKeyword.FALSE, PsiKeyword.TRUE);
  }

  private static boolean isSimplifiableImplicitAssignment(PsiIfStatement ifStatement, String thenAssignment, String elseAssignment) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);
    final PsiElement nextStatement = PsiTreeUtil.skipWhitespacesBackward(ifStatement);
    if (!(nextStatement instanceof PsiStatement)) {
      return false;
    }
    PsiStatement elseBranch = (PsiStatement)nextStatement;
    elseBranch = ControlFlowUtils.stripBraces(elseBranch);
    return isSimplifiableAssignment(thenBranch, elseBranch, thenAssignment, elseAssignment);
  }

  private static boolean isSimplifiableAssignment(PsiStatement thenBranch,
                                                  PsiStatement elseBranch,
                                                  String thenAssignment,
                                                  String elseAssignment) {
    if (!isAssignment(thenBranch, thenAssignment) || !isAssignment(elseBranch, elseAssignment)) {
      return false;
    }
    final PsiExpressionStatement thenExpressionStatement = (PsiExpressionStatement)thenBranch;
    final PsiAssignmentExpression thenExpression = (PsiAssignmentExpression)thenExpressionStatement.getExpression();
    final PsiExpressionStatement elseExpressionStatement = (PsiExpressionStatement)elseBranch;
    final PsiAssignmentExpression elseExpression = (PsiAssignmentExpression)elseExpressionStatement.getExpression();
    final IElementType thenTokenType = thenExpression.getOperationTokenType();
    if (!thenTokenType.equals(elseExpression.getOperationTokenType())) {
      return false;
    }
    final PsiExpression thenLhs = thenExpression.getLExpression();
    final PsiExpression elseLhs = elseExpression.getLExpression();
    return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenLhs, elseLhs);
  }

  private static boolean isReturn(PsiStatement statement, String value) {
    if (!(statement instanceof PsiReturnStatement)) {
      return false;
    }
    final PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
    final PsiExpression returnValue = ParenthesesUtils.stripParentheses(returnStatement.getReturnValue());
    return returnValue != null && value.equals(returnValue.getText());
  }

  private static boolean isAssignment(PsiStatement statement, String value) {
    if (!(statement instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
    final PsiExpression expression = expressionStatement.getExpression();
    if (!(expression instanceof PsiAssignmentExpression)) {
      return false;
    }
    final PsiAssignmentExpression assignment = (PsiAssignmentExpression)expression;
    final PsiExpression rhs = ParenthesesUtils.stripParentheses(assignment.getRExpression());
    return rhs != null && value.equals(rhs.getText());
  }

  private static boolean isSimplifiableAssert(PsiIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    final PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    if (!(thenBranch instanceof PsiAssertStatement)) {
      return false;
    }
    final PsiAssertStatement assertStatement = (PsiAssertStatement)thenBranch;
    return assertStatement.getAssertCondition() != null;
  }
}