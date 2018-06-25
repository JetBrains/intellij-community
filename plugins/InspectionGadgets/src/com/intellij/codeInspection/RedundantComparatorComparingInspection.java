// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

public class RedundantComparatorComparingInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher THEN_COMPARING_COMPARATOR = instanceCall(JAVA_UTIL_COMPARATOR, "thenComparing")
    .parameterTypes(JAVA_UTIL_COMPARATOR);
  private static final CallMatcher THEN_COMPARING_FUNCTION = instanceCall(JAVA_UTIL_COMPARATOR, "thenComparing")
    .parameterTypes(JAVA_UTIL_FUNCTION_FUNCTION);

  private static final CallMatcher COMPARATOR_REVERSED = instanceCall(JAVA_UTIL_COMPARATOR, "reversed").parameterCount(0);
  private static final CallMatcher REVERSE_ORDER_FOR_COMPARATOR = staticCall(JAVA_UTIL_COLLECTIONS, "reverseOrder")
    .parameterTypes(JAVA_UTIL_COMPARATOR);
  private static final CallMatcher REVERSE_ORDER_FOR_NATURAL = staticCall(JAVA_UTIL_COMPARATOR, "reverseOrder").parameterCount(0);
  private static final CallMatcher COMPARATOR_COMPARING_WITH_DOWNSTREAM =
    staticCall(JAVA_UTIL_COMPARATOR, "comparing").parameterTypes(JAVA_UTIL_FUNCTION_FUNCTION, JAVA_UTIL_COMPARATOR);

  private static final CallMatcher MIN_MAX = anyOf(
    staticCall(JAVA_UTIL_COLLECTIONS, "min", "max").parameterTypes(JAVA_UTIL_COLLECTION, JAVA_UTIL_COMPARATOR),
    instanceCall(JAVA_UTIL_STREAM_STREAM, "min", "max").parameterTypes(JAVA_UTIL_COMPARATOR),
    staticCall(JAVA_UTIL_STREAM_COLLECTORS, "minBy", "maxBy").parameterTypes(JAVA_UTIL_COMPARATOR)
  );

  private static final CallMapper<String> REPLACEMENTS = new CallMapper<String>()
    .register(staticCall(JAVA_UTIL_COMPARATOR, "comparing").parameterTypes(JAVA_UTIL_FUNCTION_FUNCTION), "thenComparing")
    .register(COMPARATOR_COMPARING_WITH_DOWNSTREAM, "thenComparing")
    .register(staticCall(JAVA_UTIL_COMPARATOR, "comparingInt").parameterCount(1), "thenComparingInt")
    .register(staticCall(JAVA_UTIL_COMPARATOR, "comparingLong").parameterCount(1), "thenComparingLong")
    .register(staticCall(JAVA_UTIL_COMPARATOR, "comparingDouble").parameterCount(1), "thenComparingDouble");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (THEN_COMPARING_COMPARATOR.test(call)) {
          checkThenComparing(call);
        }
        if (MIN_MAX.test(call)) {
          PsiExpression arg = ArrayUtil.getLastElement(call.getArgumentList().getExpressions());
          if (getPlainComparatorExpressionFromReversed(arg, new CommentTracker()) != null) {
            PsiElement nameElement = Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement());

            String maxOrMin = nameElement.getText();
            String replacement = getMaxMinReplacement(maxOrMin);
            holder.registerProblem(nameElement,
                                   InspectionsBundle.message("inspection.simplifiable.comparator.reversed.message", maxOrMin, replacement),
                                   new ReplaceMaxMinFix(replacement));
          }
        }
      }

      private void checkThenComparing(@NotNull PsiMethodCallExpression call) {
        PsiMethodCallExpression comparingCall =
          tryCast(PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]), PsiMethodCallExpression.class);
        String targetMethod = REPLACEMENTS.mapFirst(comparingCall);
        if (targetMethod == null) return;
        PsiExpressionList comparingArgs = comparingCall.getArgumentList();
        if (targetMethod.equals("thenComparing") && comparingArgs.getExpressionCount() == 1) {
          PsiMethodCallExpression copy = (PsiMethodCallExpression)call.copy();
          copy.getArgumentList().replace(comparingArgs.copy());
          // Call becomes ambiguous after simplification
          if (!THEN_COMPARING_FUNCTION.matches(copy)) return;
        }
        String name = comparingCall.getMethodExpression().getReferenceName();
        holder
          .registerProblem(comparingCall.getMethodExpression(),
                           InspectionsBundle.message("inspection.simplifiable.comparator.comparing.message", name),
                           ProblemHighlightType.LIKE_UNUSED_SYMBOL, new DeleteComparingCallFix(name, targetMethod));
      }
    };
  }

  @NotNull
  private static String getMaxMinReplacement(@NotNull String maxOrMin) {
    return (maxOrMin.startsWith("max") ? "min" : "max")+maxOrMin.substring(3);
  }

  @Nullable
  static String getPlainComparatorExpressionFromReversed(PsiExpression expression, CommentTracker ct) {
    PsiMethodCallExpression call = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiMethodCallExpression.class);
    if (call == null) return null;
    if (COMPARATOR_REVERSED.test(call)) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      return qualifier == null ? null : ct.text(qualifier);
    }
    if (REVERSE_ORDER_FOR_COMPARATOR.test(call)) {
      PsiExpression arg = call.getArgumentList().getExpressions()[0];
      PsiType type = call.getType();
      if (type != null && type.equals(arg.getType())) {
        return ct.text(arg);
      }
    }
    if (REVERSE_ORDER_FOR_NATURAL.test(call)) {
      PsiReferenceParameterList parameterList = call.getMethodExpression().getParameterList();
      return JAVA_UTIL_COMPARATOR + "."+(parameterList == null ? "" : ct.text(parameterList))+"naturalOrder()";
    }
    if (COMPARATOR_COMPARING_WITH_DOWNSTREAM.test(call) && REVERSE_ORDER_FOR_NATURAL.matches(call.getArgumentList().getExpressions()[1])) {
      return ct.text(call.getMethodExpression()) + "(" + ct.text(call.getArgumentList().getExpressions()[0]) + ")";
    }
    return null;
  }

  static class DeleteComparingCallFix implements LocalQuickFix {
    private final String mySourceMethod;
    private final String myTargetMethod;

    public DeleteComparingCallFix(String sourceMethod, String targetMethod) {
      mySourceMethod = sourceMethod;
      myTargetMethod = targetMethod;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myTargetMethod.equals("thenComparing")
             ? InspectionsBundle.message("inspection.simplifiable.comparator.fix.remove.name", mySourceMethod)
             : InspectionsBundle.message("inspection.simplifiable.comparator.fix.replace.name", mySourceMethod, myTargetMethod);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.simplifiable.comparator.fix.comparing.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression comparingCall = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (comparingCall == null) return;
      PsiMethodCallExpression thenComparingCall = PsiTreeUtil.getParentOfType(comparingCall, PsiMethodCallExpression.class);
      if (thenComparingCall == null) return;
      ExpressionUtils.bindCallTo(thenComparingCall, myTargetMethod);
      CommentTracker ct = new CommentTracker();
      thenComparingCall.getArgumentList().replace(ct.markUnchanged(comparingCall.getArgumentList()));
    }
  }

  private static class ReplaceMaxMinFix implements LocalQuickFix {
    private final String myReplacement;

    public ReplaceMaxMinFix(String replacement) {
      myReplacement = replacement;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.simplifiable.comparator.fix.reversed.name", myReplacement);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.simplifiable.comparator.fix.reversed.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression comparator = ArrayUtil.getLastElement(call.getArgumentList().getExpressions());
      if (comparator == null) return;
      CommentTracker ct = new CommentTracker();
      String reversed = getPlainComparatorExpressionFromReversed(comparator, ct);
      if (reversed == null) return;
      ct.replaceAndRestoreComments(comparator, reversed);
      ExpressionUtils.bindCallTo(call, myReplacement);
    }
  }
}
