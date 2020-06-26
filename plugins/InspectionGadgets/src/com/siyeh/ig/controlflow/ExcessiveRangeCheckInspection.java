// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfLongType;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BinaryOperator;

import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

public class ExcessiveRangeCheckInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher COLLECTION_IS_EMPTY = anyOf(instanceCall(JAVA_UTIL_COLLECTION, "isEmpty").parameterCount(0),
                                                               instanceCall(JAVA_UTIL_MAP, "isEmpty").parameterCount(0));
  private static final CallMatcher STRING_IS_EMPTY = instanceCall(JAVA_LANG_STRING, "isEmpty").parameterCount(0);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitPolyadicExpression(PsiPolyadicExpression expression) {
        IElementType type = expression.getOperationTokenType();
        boolean andChain = type.equals(JavaTokenType.ANDAND);
        if (!andChain && !type.equals(JavaTokenType.OROR)) return;
        for (List<RangeConstraint> run : StreamEx.of(expression.getOperands()).map(ExcessiveRangeCheckInspection::extractConstraint)
          .groupRuns(RangeConstraint::sameExpression)) {
          if (run.size() <= 1) continue;
          BinaryOperator<LongRangeSet> reductionOp = andChain ? LongRangeSet::intersect : LongRangeSet::unite;
          LongRangeSet set = run.stream().map(c -> c.myConstraint).reduce(reductionOp).orElse(LongRangeSet.empty());
          if (set.isEmpty()) continue;
          RangeConstraint constraint = run.get(0);
          if (!andChain) {
            set = constraint.getFullRange().subtract(set);
          }
          Long value = set.getConstantValue();
          if (value != null) {
            String text = constraint.myExpression.getText() + constraint.getExpressionSuffix();
            String replacement = text + ' ' + (andChain ? "==" : "!=") + ' ' + value;
            String message = InspectionGadgetsBundle.message("inspection.excessive.range.check.message", replacement);
            holder.registerProblem(expression,
                                   new TextRange(constraint.myRange.getStartOffset(), run.get(run.size() - 1).myRange.getEndOffset()),
                                   message, new ExcessiveRangeCheckFix(replacement));
          }
        }
      }
    };
  }

  private static RangeConstraint extractConstraint(PsiExpression expression) {
    TextRange textRange = expression.getTextRangeInParent();
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) return null;
    PsiExpression negated = BoolUtils.getNegated(expression);
    if (negated != null) {
      RangeConstraint constraint = extractConstraint(negated);
      return constraint == null ? null : constraint.negate(textRange);
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiExpression qualifier = ((PsiMethodCallExpression)expression).getMethodExpression().getQualifierExpression();
      if (qualifier != null && !SideEffectChecker.mayHaveSideEffects(qualifier)) {
        if (STRING_IS_EMPTY.matches(expression)) {
          return new RangeConstraint(textRange, qualifier, SpecialField.STRING_LENGTH, LongRangeSet.point(0));
        }
        else if (COLLECTION_IS_EMPTY.matches(expression)) {
          return new RangeConstraint(textRange, qualifier, SpecialField.COLLECTION_SIZE, LongRangeSet.point(0));
        }
      }
    }
    if (expression instanceof PsiBinaryExpression) {
      PsiBinaryExpression binOp = (PsiBinaryExpression)expression;
      RelationType rel = RelationType.fromElementType(binOp.getOperationTokenType());
      if (rel == null) return null;
      PsiExpression left = PsiUtil.skipParenthesizedExprDown(binOp.getLOperand());
      PsiExpression right = PsiUtil.skipParenthesizedExprDown(binOp.getROperand());
      if (left == null || right == null) return null;
      if (!TypeConversionUtil.isIntegralNumberType(left.getType()) || !TypeConversionUtil.isIntegralNumberType(right.getType())) {
        return null;
      }
      Number leftNum = JavaPsiMathUtil.getNumberFromLiteral(left);
      Number rightNum = JavaPsiMathUtil.getNumberFromLiteral(right);
      LongRangeSet set;
      PsiExpression compared;
      if (leftNum instanceof Integer || leftNum instanceof Long) {
        set = LongRangeSet.point(leftNum.longValue()).fromRelation(rel.getFlipped());
        compared = right;
      }
      else if (rightNum instanceof Integer || rightNum instanceof Long) {
        set = LongRangeSet.point(rightNum.longValue()).fromRelation(rel);
        compared = left;
      }
      else {
        return null;
      }
      return RangeConstraint.create(textRange, compared, set);
    }
    return null;
  }

  private static final class RangeConstraint {
    private final @NotNull TextRange myRange;
    private final @NotNull PsiExpression myExpression;
    private final @Nullable SpecialField myField;
    private final @NotNull LongRangeSet myConstraint;

    private RangeConstraint(@NotNull TextRange range,
                            @NotNull PsiExpression expression,
                            @Nullable SpecialField field,
                            @NotNull LongRangeSet constraint) {
      myRange = range;
      myExpression = expression;
      myField = field;
      myConstraint = constraint.intersect(getFullRange());
    }

    RangeConstraint negate(TextRange newTextRange) {
      return new RangeConstraint(newTextRange, myExpression, myField, getFullRange().subtract(myConstraint));
    }

    static boolean sameExpression(RangeConstraint left, RangeConstraint right) {
      return left != null && right != null && left.myField == right.myField &&
             EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(left.myExpression, right.myExpression);
    }

    @NotNull
    LongRangeSet getFullRange() {
      LongRangeSet result;
      if (myField != null) {
        result = DfLongType.extractRange(myField.getDefaultValue(false));
      }
      else {
        result = LongRangeSet.fromType(myExpression.getType());
      }
      return result == null ? LongRangeSet.all() : result;
    }

    String getExpressionSuffix() {
      if (myField == null) return "";
      switch (myField) {
        case ARRAY_LENGTH:
          return ".length";
        case STRING_LENGTH:
          return ".length()";
        case COLLECTION_SIZE:
          return ".size()";
        default:
          return "";
      }
    }

    @Nullable
    static RangeConstraint create(TextRange textRange, PsiExpression expr, LongRangeSet set) {
      SpecialField field = null;
      PsiReferenceExpression ref = expr instanceof PsiReferenceExpression ? (PsiReferenceExpression)expr :
                                   expr instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression)expr).getMethodExpression() : null;
      if (ref != null) {
        PsiExpression qualifier = ref.getQualifierExpression();
        if (qualifier != null) {
          field = SpecialField.findSpecialField(ref.resolve());
          if (field != null) {
            expr = qualifier;
          }
        }
      }
      if (SideEffectChecker.mayHaveSideEffects(expr)) {
        return null;
      }
      return new RangeConstraint(textRange, expr, field, set);
    }
  }

  private static class ExcessiveRangeCheckFix implements LocalQuickFix {
    private final String myReplacement;

    ExcessiveRangeCheckFix(String replacement) {
      myReplacement = replacement;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myReplacement);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.excessive.range.check.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiPolyadicExpression expression = ObjectUtils.tryCast(descriptor.getStartElement(), PsiPolyadicExpression.class);
      if (expression == null) return;
      TextRange range = descriptor.getTextRangeInElement();
      PsiExpression[] allOperands = expression.getOperands();
      List<PsiExpression> operands = ContainerUtil.filter(allOperands, op -> range.contains(op.getTextRangeInParent()));
      if (operands.size() < 2) return;
      PsiExpression firstOperand = operands.get(0);
      PsiExpression lastOperand = operands.get(operands.size() - 1);
      RangeConstraint constraint = extractConstraint(firstOperand);
      if (constraint == null) return;
      CommentTracker ct = new CommentTracker();
      ct.markUnchanged(constraint.myExpression);
      PsiElement[] allChildren = expression.getChildren();
      PsiElement lastInPrefix = firstOperand.getPrevSibling();
      String fullReplacement = "";
      if (lastInPrefix != null) {
        fullReplacement += ct.rangeText(allChildren[0], lastInPrefix);
      }
      fullReplacement += myReplacement;
      PsiElement firstInSuffix = lastOperand.getNextSibling();
      if (firstInSuffix != null) {
        PsiElement lastInSuffix = allChildren[allChildren.length - 1];
        fullReplacement += ct.rangeText(firstInSuffix, lastInSuffix);
      }
      ct.replaceAndRestoreComments(expression, fullReplacement);
    }
  }
}
