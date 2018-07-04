// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ComparatorResultComparisonInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher COMPARE_METHOD = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COMPARATOR, "compare").parameterCount(2),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_COMPARABLE, "compareTo").parameterCount(1)
  );
  public static final LongRangeSet NEGATIVE_INTEGERS = LongRangeSet.range(Integer.MIN_VALUE, -1);
  public static final LongRangeSet POSITIVE_INTEGERS = LongRangeSet.range(1, Integer.MAX_VALUE);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (!COMPARE_METHOD.test(call)) return;
        checkComparison(call);
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(call.getParent());
        if (parent instanceof PsiLocalVariable) {
          PsiLocalVariable var = (PsiLocalVariable)parent;
          PsiCodeBlock block = PsiTreeUtil.getParentOfType(var, PsiCodeBlock.class);
          PsiExpression initializer = var.getInitializer();
          if (block != null && initializer != null) {
            for (PsiElement element : DefUseUtil.getRefs(block, var, initializer)) {
              checkComparison(element);
            }
          }
        }
      }

      private void checkComparison(PsiElement compareExpression) {
        PsiBinaryExpression binOp =
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(compareExpression.getParent()), PsiBinaryExpression.class);
        if (binOp == null) return;
        PsiJavaToken sign = binOp.getOperationSign();
        IElementType tokenType = sign.getTokenType();
        RelationType relation = RelationType.fromElementType(tokenType);
        if (relation == null) return;
        PsiExpression constOperand =
          PsiTreeUtil.isAncestor(binOp.getLOperand(), compareExpression, false) ? binOp.getROperand() : binOp.getLOperand();
        if (constOperand == null) return;
        Object constantExpression = ExpressionUtils.computeConstantExpression(constOperand);
        if (!(constantExpression instanceof Integer)) return;
        int value = ((Integer)constantExpression).intValue();
        if (value == 0) return;
        boolean yodaCondition = constOperand == binOp.getLOperand();
        if (yodaCondition) {
          relation = relation.getFlipped();
        }
        LongRangeSet rangeSet = LongRangeSet.point(value).fromRelation(relation);
        if (coversPartially(rangeSet, NEGATIVE_INTEGERS)) {
          register(sign, yodaCondition, rangeSet.intersects(POSITIVE_INTEGERS) ? null : RelationType.LT);
        } else if (coversPartially(rangeSet, POSITIVE_INTEGERS)) {
          register(sign, yodaCondition, rangeSet.intersects(NEGATIVE_INTEGERS) ? null : RelationType.GT);
        }
      }

      private boolean coversPartially(LongRangeSet testedRange, LongRangeSet coveredRange) {
        LongRangeSet intersection = testedRange.intersect(coveredRange);
        return !intersection.isEmpty() && !coveredRange.subtract(intersection).isEmpty();
      }

      private void register(PsiJavaToken sign, boolean yodaCondition, RelationType relationType) {
        LocalQuickFix[] fixes =
          relationType == null ? LocalQuickFix.EMPTY_ARRAY : new LocalQuickFix[]{new ComparatorComparisonFix(yodaCondition, relationType)};
        holder.registerProblem(sign, InspectionsBundle.message("inspection.comparator.result.comparison.problem.display.name"), fixes);
      }
    };
  }

  private static class ComparatorComparisonFix implements LocalQuickFix {
    private final boolean myYodaCondition;
    private final RelationType myRelation;

    public ComparatorComparisonFix(boolean yodaCondition, RelationType relation) {
      myYodaCondition = yodaCondition;
      myRelation = relation;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("replace.with", getReplacement());
    }

    @NotNull
    private String getReplacement() {
      return myYodaCondition ? "0 " + myRelation.getFlipped() : myRelation + " 0";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.comparator.result.comparison.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiBinaryExpression binOp = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiBinaryExpression.class);
      if (binOp == null) return;
      CommentTracker ct = new CommentTracker();
      String replacement;
      if(myYodaCondition) {
        PsiExpression operand = binOp.getROperand();
        if (operand == null) return;
        replacement = getReplacement() + ct.text(operand);
      } else {
        replacement = ct.text(binOp.getLOperand()) + getReplacement();
      }
      ct.replaceAndRestoreComments(binOp, replacement);
    }
  }
}
