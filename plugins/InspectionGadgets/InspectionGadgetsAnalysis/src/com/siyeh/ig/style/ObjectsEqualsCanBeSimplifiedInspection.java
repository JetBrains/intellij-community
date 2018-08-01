// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ObjectsEqualsCanBeSimplifiedInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher OBJECTS_EQUALS = CallMatcher.staticCall(
    CommonClassNames.JAVA_UTIL_OBJECTS, "equals").parameterCount(2);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel7OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (!OBJECTS_EQUALS.test(call)) return;
        PsiExpression arg1 = call.getArgumentList().getExpressions()[0];
        PsiClass argClass = PsiUtil.resolveClassInClassTypeOnly(arg1.getType());
        if (argClass == null) return;
        if (NullabilityUtil.getExpressionNullability(arg1, true) == Nullability.NOT_NULL) {
          PsiMethod[] equalsMethods = argClass.findMethodsByName("equals", true);
          for (PsiMethod method : equalsMethods) {
            if (!method.hasModifierProperty(PsiModifier.STATIC) &&
                method.getParameterList().getParametersCount() == 1 &&
                !TypeUtils.isJavaLangObject(method.getParameterList().getParameters()[0].getType())) {
              // After replacement may be linked to overloaded equals method
              // even if not, the code becomes more fragile, so let's not suggest the replacement if equals(SomeType) is defined.
              return;
            }
          }
          PsiElement nameElement = Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement());
          holder.registerProblem(nameElement, InspectionsBundle.message("inspection.objects.equals.can.be.simplified.message"),
                                 new ReplaceWithEqualsFix());
        }
      }
    };
  }

  private static class ReplaceWithEqualsFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.objects.equals.can.be.simplified.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 2) return;
      CommentTracker ct = new CommentTracker();
      String replacement = ct.text(args[0], PsiPrecedenceUtil.METHOD_CALL_PRECEDENCE) + ".equals(" + ct.text(args[1]) + ")";
      ct.replaceAndRestoreComments(call, replacement);
    }
  }
}
