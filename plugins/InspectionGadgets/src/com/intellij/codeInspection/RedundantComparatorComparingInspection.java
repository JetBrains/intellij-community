// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_COMPARATOR;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public class RedundantComparatorComparingInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher THEN_COMPARING_COMPARATOR = instanceCall(JAVA_UTIL_COMPARATOR, "thenComparing")
    .parameterTypes(JAVA_UTIL_COMPARATOR);
  private static final CallMatcher THEN_COMPARING_FUNCTION = instanceCall(JAVA_UTIL_COMPARATOR, "thenComparing")
    .parameterTypes(JAVA_UTIL_FUNCTION_FUNCTION);

  private static final CallMapper<String> REPLACEMENTS = new CallMapper<String>()
    .register(staticCall(JAVA_UTIL_COMPARATOR, "comparing").parameterTypes(JAVA_UTIL_FUNCTION_FUNCTION), "thenComparing")
    .register(staticCall(JAVA_UTIL_COMPARATOR, "comparing").parameterTypes(JAVA_UTIL_FUNCTION_FUNCTION, JAVA_UTIL_COMPARATOR),
              "thenComparing")
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
        if (!THEN_COMPARING_COMPARATOR.test(call)) return;
        PsiMethodCallExpression comparingCall =
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]), PsiMethodCallExpression.class);
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
                           InspectionsBundle.message("inspection.redundant.comparator.comparing.message", name),
                           ProblemHighlightType.LIKE_UNUSED_SYMBOL, new DeleteComparingCallFix(name, targetMethod));
      }
    };
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
             ? InspectionsBundle.message("inspection.redundant.comparator.comparing.fix.remove.name", mySourceMethod)
             : InspectionsBundle.message("inspection.redundant.comparator.comparing.fix.replace.name", mySourceMethod, myTargetMethod);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.redundant.comparator.comparing.fix.family.name");
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
}
