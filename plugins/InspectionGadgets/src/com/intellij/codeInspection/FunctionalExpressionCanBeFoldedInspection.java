// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class FunctionalExpressionCanBeFoldedInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        final PsiExpression qualifierExpression = expression.getQualifierExpression();
        final PsiElement referenceNameElement = expression.getReferenceNameElement();
        doCheckCall(expression, () -> expression.resolve(), qualifierExpression, referenceNameElement,
                    "Method reference can be replaced with qualifier");
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression lambdaExpression) {
        PsiElement body = lambdaExpression.getBody();
        PsiExpression asMethodReference = LambdaCanBeMethodReferenceInspection
          .canBeMethodReferenceProblem(body, lambdaExpression.getParameterList().getParameters(), lambdaExpression.getFunctionalInterfaceType(), null);
        if (asMethodReference instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)asMethodReference).getMethodExpression();
          PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
          doCheckCall(lambdaExpression, () -> ((PsiMethodCallExpression)asMethodReference).resolveMethod(), qualifierExpression, asMethodReference,
                      "Lambda can be replaced with call qualifier");
        }
      }

      private void doCheckCall(PsiFunctionalExpression expression,
                               Supplier<PsiElement> resolver,
                               PsiExpression qualifierExpression,
                               PsiElement referenceNameElement,
                               final String errorMessage) {
        if (qualifierExpression != null && referenceNameElement != null && !(qualifierExpression instanceof PsiSuperExpression)) {
          final PsiType qualifierType = qualifierExpression.getType();
          if (qualifierType != null) {
            //don't get ground type as check is required over expected type instead
            final PsiType functionalInterfaceType = LambdaUtil.getFunctionalInterfaceType(expression, true);
            final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
            if (interfaceMethod != null) {
              final PsiElement resolve = resolver.get();
              if (resolve instanceof PsiMethod &&
                  (interfaceMethod == resolve || MethodSignatureUtil.isSuperMethod(interfaceMethod, (PsiMethod)resolve)) &&
                  TypeConversionUtil.isAssignable(functionalInterfaceType, qualifierType)) {
                holder.registerProblem(referenceNameElement, errorMessage, new ReplaceMethodRefWithQualifierFix());
              }
            }
          }
        }
      }
    };
  }

  private static class ReplaceMethodRefWithQualifierFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with qualifier";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element != null ? element.getParent() : null;
      if (parent instanceof PsiMethodReferenceExpression) {
        final PsiExpression qualifierExpression = ((PsiMethodReferenceExpression)parent).getQualifierExpression();
        if (qualifierExpression != null) {
          parent.replace(qualifierExpression);
        }
      }
      else if (parent instanceof PsiLambdaExpression) {
        PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(((PsiLambdaExpression)parent).getBody());
        if (expression instanceof PsiMethodCallExpression) {
          PsiExpression qualifierExpression = ((PsiMethodCallExpression)expression).getMethodExpression().getQualifierExpression();
          if (qualifierExpression != null) {
            parent.replace(qualifierExpression);
          }
        }
      }
    }
  }
}
