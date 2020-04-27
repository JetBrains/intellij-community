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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.SetInspectionOptionFix;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.style.ConditionalModel;
import com.siyeh.ig.style.IfConditionalModel;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

public class TrivialIfInspection extends BaseInspection implements CleanupLocalInspectionTool {

  public boolean ignoreChainedIf = false;

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "RedundantIfStatement";
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("trivial.if.option.ignore.chained"), this, "ignoreChainedIf");
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
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    boolean chainedIf = (boolean)infos[0];
    if (chainedIf) {
      return new InspectionGadgetsFix[]{
        new TrivialIfFix(),
        new DelegatingFix(new SetInspectionOptionFix(
          this, "ignoreChainedIf", InspectionGadgetsBundle.message("trivial.if.option.ignore.chained"), true))
      };
    }
    return new InspectionGadgetsFix[]{new TrivialIfFix()};
  }

  @Nullable
  private static String getReplacementText(ConditionalModel model, CommentTracker ct) {
    PsiLiteralExpression thenLiteral = ExpressionUtils.getLiteral(model.getThenExpression());
    PsiLiteralExpression elseLiteral = ExpressionUtils.getLiteral(model.getElseExpression());
    if (thenLiteral != null && elseLiteral != null) {
      if (Boolean.TRUE.equals(thenLiteral.getValue()) && Boolean.FALSE.equals(elseLiteral.getValue())) {
        return ct.text(model.getCondition());
      }
      if (Boolean.FALSE.equals(thenLiteral.getValue()) && Boolean.TRUE.equals(elseLiteral.getValue())) {
        return BoolUtils.getNegatedExpressionText(model.getCondition(), ct);
      }
    }
    PsiExpression replacement = getRedundantComparisonReplacement(model);
    if (replacement != null) {
      return ct.text(replacement);
    }
    return null;
  }

  private static PsiExpression getRedundantComparisonReplacement(@NotNull ConditionalModel model) {
    @NotNull PsiExpression thenExpression = model.getThenExpression();
    @NotNull PsiExpression elseExpression = model.getElseExpression();
    PsiBinaryExpression binOp = tryCast(PsiUtil.skipParenthesizedExprDown(model.getCondition()), PsiBinaryExpression.class);
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
    if (equivalence.expressionsAreEquivalent(left, thenExpression) && equivalence.expressionsAreEquivalent(right, elseExpression) ||
        equivalence.expressionsAreEquivalent(right, thenExpression) && equivalence.expressionsAreEquivalent(left, elseExpression)) {
      return equals ? elseExpression : thenExpression;
    }
    return null;
  }

  private static class TrivialIfFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("trivial.if.fix.family.name");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement ifKeywordElement = descriptor.getPsiElement();
      final PsiIfStatement statement = (PsiIfStatement)ifKeywordElement.getParent();
      simplify(statement);
    }
  }

  private static void simplify(PsiIfStatement statement) {
    IfConditionalModel model = IfConditionalModel.from(statement, true);
    if (model != null) {
      CommentTracker ct = new CommentTracker();
      String text = getReplacementText(model, ct);
      if (text != null) {
        if (model.getElseExpression().textMatches(text) && !PsiTreeUtil.isAncestor(statement, model.getElseBranch(), false)) {
          ct.deleteAndRestoreComments(statement);
        } else {
          ct.replace(model.getThenExpression(), text);
          ct.replaceAndRestoreComments(statement, model.getThenBranch());
          PsiStatement elseBranch = model.getElseBranch();
          if (elseBranch.isValid() && (elseBranch instanceof PsiExpressionStatement || !ControlFlowUtils.isReachable(elseBranch))) {
            PsiElement sibling = elseBranch.getPrevSibling();
            if (sibling instanceof PsiWhiteSpace) {
              sibling.delete();
            }
            new CommentTracker().deleteAndRestoreComments(elseBranch);
          }
        }
      }
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

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitIfStatement(@NotNull PsiIfStatement ifStatement) {
        super.visitIfStatement(ifStatement);
        boolean chainedIf = PsiTreeUtil.skipWhitespacesAndCommentsBackward(ifStatement) instanceof PsiIfStatement ||
                            (ifStatement.getParent() instanceof PsiIfStatement &&
                             ((PsiIfStatement)ifStatement.getParent()).getElseBranch() == ifStatement);
        if (ignoreChainedIf && chainedIf && !isOnTheFly()) return;
        final PsiExpression condition = ifStatement.getCondition();
        if (condition == null) {
          return;
        }
        if (isTrivial(ifStatement)) {
          PsiElement anchor = Objects.requireNonNull(ifStatement.getFirstChild());
          ProblemHighlightType level =
            ignoreChainedIf && chainedIf ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
          boolean addIgnoreFix = chainedIf && !ignoreChainedIf &&
                                 !InspectionProjectProfileManager.isInformationLevel(getShortName(), ifStatement);
          registerError(anchor, level, addIgnoreFix);
        }
      }
    };
  }

  private static boolean isTrivial(PsiIfStatement ifStatement) {
    if (PsiUtilCore.hasErrorElementChild(ifStatement)) {
      return false;
    }
    IfConditionalModel model = IfConditionalModel.from(ifStatement, true);
    if (model != null && getReplacementText(model, new CommentTracker()) != null) {
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
}