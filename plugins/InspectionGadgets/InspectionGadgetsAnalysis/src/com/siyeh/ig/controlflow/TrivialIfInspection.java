/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
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
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
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
      repaceSimplifiableReturn(statement);
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
  }

  private static void replaceSimplifiableImplicitReturn(
    PsiIfStatement statement) throws IncorrectOperationException {
    final PsiExpression condition = statement.getCondition();
    if (condition == null) {
      return;
    }
    final String conditionText = condition.getText();
    final PsiElement nextStatement = PsiTreeUtil.skipSiblingsForward(statement, PsiWhiteSpace.class);
    @NonNls final String newStatement = "return " + conditionText + ';';
    PsiReplacementUtil.replaceStatement(statement, newStatement);
    assert nextStatement != null;
    nextStatement.delete();
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
    PsiReplacementUtil.replaceStatement(statement, newStatement);
  }

  private static void replaceSimplifiableAssignment(PsiIfStatement statement)
    throws IncorrectOperationException {
    final PsiExpression condition = statement.getCondition();
    if (condition == null) {
      return;
    }
    final Collection<PsiComment> comments = ContainerUtil.map(PsiTreeUtil.findChildrenOfType(statement, PsiComment.class),
                                                              new Function<PsiComment, PsiComment>() {
                                                                @Override
                                                                public PsiComment fun(PsiComment comment) {
                                                                  return (PsiComment)comment.copy();
                                                                }
                                                              });
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

  private static void replaceSimplifiableImplicitAssignment(PsiIfStatement statement) throws IncorrectOperationException {
    final PsiElement prevStatement = PsiTreeUtil.skipSiblingsBackward(statement, PsiWhiteSpace.class);
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
    if (assignmentStatement == null) {
      return;
    }
    final PsiAssignmentExpression assignmentExpression =
      (PsiAssignmentExpression)assignmentStatement.getExpression();
    final PsiJavaToken operator =
      assignmentExpression.getOperationSign();
    final String operand = operator.getText();
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final String lhsText = lhs.getText();
    PsiReplacementUtil.replaceStatement(statement, lhsText + operand + conditionText + ';');
    prevStatement.delete();
  }

  private static void replaceSimplifiableImplicitAssignmentNegated(PsiIfStatement statement) {
    final PsiElement prevStatement = PsiTreeUtil.skipSiblingsBackward(statement, PsiWhiteSpace.class);
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
    final PsiJavaToken operator =
      assignmentExpression.getOperationSign();
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
    final PsiElement nextStatement = PsiTreeUtil.skipSiblingsForward(statement, PsiWhiteSpace.class);
    if (nextStatement == null) {
      return;
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
    if (isSimplifiableAssignment(ifStatement)) {
      return true;
    }
    if (isSimplifiableReturn(ifStatement)) {
      return true;
    }
    if (isSimplifiableImplicitReturn(ifStatement)) {
      return true;
    }
    if (isSimplifiableAssignmentNegated(ifStatement)) {
      return true;
    }
    if (isSimplifiableReturnNegated(ifStatement)) {
      return true;
    }
    if (isSimplifiableImplicitReturnNegated(ifStatement)) {
      return true;
    }
    if (isSimplifiableImplicitAssignment(ifStatement)) {
      return true;
    }
    if (isSimplifiableImplicitAssignmentNegated(ifStatement)) {
      return true;
    }
    return false;
  }

  public static boolean isSimplifiableImplicitReturn(PsiIfStatement ifStatement) {
    return isSimplifiableImplicitReturn(ifStatement, PsiKeyword.TRUE, PsiKeyword.FALSE);
  }

  public static boolean isSimplifiableImplicitReturnNegated(PsiIfStatement ifStatement) {
    return isSimplifiableImplicitReturn(ifStatement, PsiKeyword.FALSE, PsiKeyword.TRUE);
  }

  public static boolean isSimplifiableImplicitReturn(PsiIfStatement ifStatement, String thenReturn, String elseReturn) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);
    final PsiElement nextStatement = PsiTreeUtil.skipSiblingsForward(ifStatement, PsiWhiteSpace.class);
    if (!(nextStatement instanceof PsiStatement)) {
      return false;
    }

    final PsiStatement elseBranch = (PsiStatement)nextStatement;
    return ConditionalUtils.isReturn(thenBranch, thenReturn) && ConditionalUtils.isReturn(elseBranch, elseReturn);
  }

  public static boolean isSimplifiableReturn(PsiIfStatement ifStatement) {
    return isSimplifiableReturn(ifStatement, PsiKeyword.TRUE, PsiKeyword.FALSE);
  }

  public static boolean isSimplifiableReturnNegated(PsiIfStatement ifStatement) {
    return isSimplifiableReturn(ifStatement, PsiKeyword.FALSE, PsiKeyword.TRUE);
  }

  public static boolean isSimplifiableReturn(PsiIfStatement ifStatement, String thenReturn, String elseReturn) {
    final PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    final PsiStatement elseBranch = ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
    return ConditionalUtils.isReturn(thenBranch, thenReturn) && ConditionalUtils.isReturn(elseBranch, elseReturn);
  }

  public static boolean isSimplifiableAssignment(PsiIfStatement ifStatement) {
    return isSimplifiableAssignment(ifStatement, PsiKeyword.TRUE, PsiKeyword.FALSE);
  }

  public static boolean isSimplifiableAssignmentNegated(PsiIfStatement ifStatement) {
    return isSimplifiableAssignment(ifStatement, PsiKeyword.FALSE, PsiKeyword.TRUE);
  }

  public static boolean isSimplifiableAssignment(PsiIfStatement ifStatement, String thenAssignment, String elseAssignment) {
    final PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    final PsiStatement elseBranch = ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
    return isSimplifiableAssignment(thenBranch, elseBranch, thenAssignment, elseAssignment);
  }

  public static boolean isSimplifiableImplicitAssignment(PsiIfStatement ifStatement) {
    return isSimplifiableImplicitAssignment(ifStatement, PsiKeyword.TRUE, PsiKeyword.FALSE);
  }

  public static boolean isSimplifiableImplicitAssignmentNegated(PsiIfStatement ifStatement) {
    return isSimplifiableImplicitAssignment(ifStatement, PsiKeyword.FALSE, PsiKeyword.TRUE);
  }

  public static boolean isSimplifiableImplicitAssignment(PsiIfStatement ifStatement, String thenAssignment, String elseAssignment) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ControlFlowUtils.stripBraces(thenBranch);
    final PsiElement nextStatement =
      PsiTreeUtil.skipSiblingsBackward(ifStatement, PsiWhiteSpace.class);
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
    if (ConditionalUtils.isAssignment(thenBranch, thenAssignment) &&
        ConditionalUtils.isAssignment(elseBranch, elseAssignment)) {
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
      return EquivalenceChecker.expressionsAreEquivalent(thenLhs, elseLhs);
    }
    else {
      return false;
    }
  }
}