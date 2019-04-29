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
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

public class TrivialIfInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Pattern(VALID_ID_PATTERN)
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
    IfBranches branches = IfBranches.fromAssignment(statement);
    if (branches != null) {
      replaceSimplifiableAssignment(statement, branches);
      return;
    }
    branches = IfBranches.fromReturn(statement);
    if (branches != null) {
      replaceSimplifiableReturn(statement, branches);
      return;
    }
    branches = IfBranches.fromImplicitReturn(statement);
    if (branches != null) {
      replaceSimplifiableImplicitReturn(statement, branches);
      return;
    }
    branches = IfBranches.fromImplicitAssignment(statement);
    if (branches != null) {
      replaceSimplifiableImplicitAssignment(statement, branches);
      return;
    }
    if (isSimplifiableAssert(statement)) {
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

  private static void replaceSimplifiableImplicitReturn(PsiIfStatement statement, IfBranches branches) {
    PsiReturnStatement returnStatement = PsiTreeUtil.getParentOfType(branches.myThenExpression, PsiReturnStatement.class);
    if (returnStatement == null) return;
    PsiReturnStatement nextStatement = ControlFlowUtils.getNextReturnStatement(statement);
    if (nextStatement == null) return;
    CommentTracker ct = new CommentTracker();
    String replacementText = branches.getReplacementText(ct);
    if (replacementText == null) return;
    if (Objects.requireNonNull(nextStatement.getReturnValue()).textMatches(replacementText)) {
      ct.deleteAndRestoreComments(statement);
    } else {
      ct.replace(Objects.requireNonNull(returnStatement.getReturnValue()), replacementText);
      ct.replaceAndRestoreComments(statement, returnStatement);
      if (!ControlFlowUtils.isReachable(nextStatement)) {
        new CommentTracker().deleteAndRestoreComments(nextStatement);
      }
    }
  }

  private static void replaceSimplifiableReturn(PsiIfStatement statement, IfBranches branches) {
    PsiReturnStatement returnStatement = PsiTreeUtil.getParentOfType(branches.myThenExpression, PsiReturnStatement.class);
    if (returnStatement == null) return;
    CommentTracker ct = new CommentTracker();
    String replacementText = branches.getReplacementText(ct);
    if (replacementText == null) return;
    ct.replace(Objects.requireNonNull(returnStatement.getReturnValue()), replacementText);
    ct.replaceAndRestoreComments(statement, returnStatement);
  }

  private static void replaceSimplifiableAssignment(PsiIfStatement statement, IfBranches branches) {
    PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(branches.myThenExpression, PsiAssignmentExpression.class);
    if (assignment == null) return;
    PsiElement assignmentParent = assignment.getParent();
    assert assignmentParent instanceof PsiExpressionStatement;
    CommentTracker ct = new CommentTracker();
    String replacementText = branches.getReplacementText(ct);
    if (replacementText == null) return;
    ct.replace(Objects.requireNonNull(assignment.getRExpression()), replacementText);
    ct.replaceAndRestoreComments(statement, assignmentParent);
  }

  private static void replaceSimplifiableImplicitAssignment(PsiIfStatement statement, IfBranches branches) {
    PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(branches.myThenExpression, PsiAssignmentExpression.class);
    if (assignment == null) return;
    PsiElement assignmentParent = assignment.getParent();
    assert assignmentParent instanceof PsiExpressionStatement;
    PsiStatement prevStatement = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsBackward(statement), PsiStatement.class);
    if (prevStatement == null) return;
    CommentTracker ct = new CommentTracker();
    ct.delete(prevStatement);
    String replacementText = branches.getReplacementText(ct);
    if (replacementText == null) return;
    ct.replace(Objects.requireNonNull(assignment.getRExpression()), replacementText);
    ct.replaceAndRestoreComments(statement, assignmentParent);
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
    IfBranches branches;
    branches = IfBranches.fromAssignment(ifStatement);
    if (branches == null) {
      branches = IfBranches.fromReturn(ifStatement);
    }
    if (branches == null) {
      branches = IfBranches.fromImplicitReturn(ifStatement);
    }
    if (branches == null) {
      branches = IfBranches.fromImplicitAssignment(ifStatement);
    }
    if (branches != null && (branches.isFalseTrue() || branches.isTrueFalse() || branches.getRedundantComparisonReplacement() != null)) {
      return true;
    }
    return isSimplifiableAssert(ifStatement);
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

  private static class IfBranches {
    final @NotNull PsiExpression myCondition;
    final @NotNull PsiExpression myThenExpression;
    final @NotNull PsiExpression myElseExpression;

    IfBranches(@NotNull PsiExpression condition, @NotNull PsiExpression thenExpression, @NotNull PsiExpression elseExpression) {
      myCondition = condition;
      myThenExpression = thenExpression;
      myElseExpression = elseExpression;
    }

    @Nullable
    String getReplacementText(CommentTracker ct) {
      if (isTrueFalse()) {
        return ct.text(myCondition);
      }
      if (isFalseTrue()) {
        return BoolUtils.getNegatedExpressionText(myCondition, ct);
      }
      PsiExpression replacement = getRedundantComparisonReplacement();
      if (replacement != null) {
        return ct.text(replacement);
      }
      return null;
    }

    PsiExpression getRedundantComparisonReplacement() {
      PsiBinaryExpression binOp = tryCast(PsiUtil.skipParenthesizedExprDown(myCondition), PsiBinaryExpression.class);
      if (binOp == null) return null;
      IElementType tokenType = binOp.getOperationTokenType();
      boolean equals = tokenType.equals(JavaTokenType.EQEQ);
      if (!equals && !tokenType.equals(JavaTokenType.NE)) return null;
      PsiExpression left = PsiUtil.skipParenthesizedExprDown(binOp.getLOperand());
      PsiExpression right = PsiUtil.skipParenthesizedExprDown(binOp.getROperand());
      if (!ExpressionUtils.isSafelyRecomputableExpression(left) || !ExpressionUtils.isSafelyRecomputableExpression(right)) return null;
      if (TypeConversionUtil.isFloatOrDoubleType(left.getType()) && TypeConversionUtil.isFloatOrDoubleType(right.getType())) {
        // Simplifying the comparison of two floats/doubles like "if(a == 0.0) return 0.0; else return a;" 
        // will cause a semantics change for "a == -0.0" 
        return null;
      }
      EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
      if (equivalence.expressionsAreEquivalent(left, myThenExpression) && equivalence.expressionsAreEquivalent(right, myElseExpression) ||
          equivalence.expressionsAreEquivalent(right, myThenExpression) && equivalence.expressionsAreEquivalent(left, myElseExpression)) {
        return equals ? myElseExpression : myThenExpression;
      }
      return null;
    }

    boolean isTrueFalse() {
      return ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(myThenExpression), Boolean.TRUE) &&
             ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(myElseExpression), Boolean.FALSE);
    }

    boolean isFalseTrue() {
      return ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(myThenExpression), Boolean.FALSE) &&
             ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(myElseExpression), Boolean.TRUE);
    }

    static IfBranches fromAssignment(PsiIfStatement ifStatement) {
      return fromAssignment(ifStatement, ifStatement.getThenBranch(), ifStatement.getElseBranch(), true);
    }

    static IfBranches fromImplicitAssignment(PsiIfStatement ifStatement) {
      if (ifStatement.getElseBranch() != null) return null;
      PsiStatement prevStatement = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsBackward(ifStatement), PsiStatement.class);
      return fromAssignment(ifStatement, ifStatement.getThenBranch(), prevStatement, false);
    }

    private static IfBranches fromAssignment(PsiIfStatement ifStatement,
                                             PsiStatement thenBranch,
                                             PsiStatement elseBranch,
                                             boolean explicit) {
      if (ifStatement.getCondition() == null) return null;
      thenBranch = ControlFlowUtils.stripBraces(thenBranch);
      elseBranch = ControlFlowUtils.stripBraces(elseBranch);
      if (!(thenBranch instanceof PsiExpressionStatement) || !(elseBranch instanceof PsiExpressionStatement)) return null;
      PsiAssignmentExpression thenExpression = tryCast(((PsiExpressionStatement)thenBranch).getExpression(), PsiAssignmentExpression.class);
      PsiAssignmentExpression elseExpression = tryCast(((PsiExpressionStatement)elseBranch).getExpression(), PsiAssignmentExpression.class);
      if (thenExpression == null || elseExpression == null) return null;
      IElementType thenTokenType = thenExpression.getOperationTokenType();
      if (!thenTokenType.equals(elseExpression.getOperationTokenType())) return null;
      if (!explicit && !thenTokenType.equals(JavaTokenType.EQ)) return null;
      PsiExpression thenLhs = thenExpression.getLExpression();
      PsiExpression elseLhs = elseExpression.getLExpression();
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenLhs, elseLhs)) return null;
      PsiExpression thenRhs = thenExpression.getRExpression();
      PsiExpression elseRhs = elseExpression.getRExpression();
      if (thenRhs == null || elseRhs == null) return null;
      if (!explicit && !ExpressionUtils.isSafelyRecomputableExpression(elseRhs)) return null;
      return new IfBranches(ifStatement.getCondition(), thenRhs, elseRhs);
    }

    static IfBranches fromReturn(PsiIfStatement ifStatement) {
      if (ifStatement.getCondition() == null) return null;
      PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      PsiStatement elseBranch = ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
      if (!(thenBranch instanceof PsiReturnStatement) || !(elseBranch instanceof PsiReturnStatement)) return null;
      PsiExpression thenValue = ((PsiReturnStatement)thenBranch).getReturnValue();
      PsiExpression elseValue = ((PsiReturnStatement)elseBranch).getReturnValue();
      if (thenValue == null || elseValue == null) return null;
      return new IfBranches(ifStatement.getCondition(), thenValue, elseValue);
    }

    static IfBranches fromImplicitReturn(PsiIfStatement ifStatement) {
      if (ifStatement.getCondition() == null || ifStatement.getElseBranch() != null) return null;
      PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      if (!(thenBranch instanceof PsiReturnStatement)) return null;
      PsiReturnStatement elseReturn = ControlFlowUtils.getNextReturnStatement(ifStatement);
      if (elseReturn == null) return null;
      PsiExpression thenValue = ((PsiReturnStatement)thenBranch).getReturnValue();
      PsiExpression elseValue = elseReturn.getReturnValue();
      if (thenValue == null || elseValue == null) return null;
      return new IfBranches(ifStatement.getCondition(), thenValue, elseValue);
    }
  }
}