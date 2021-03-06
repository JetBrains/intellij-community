// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightNamesUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;

public class UnsafeReturnStatementVisitorInspection extends DevKitInspectionBase {

  private static final @NonNls String BASE_WALKING_VISITOR_NAME = JavaRecursiveElementWalkingVisitor.class.getName();
  private static final @NonNls String BASE_VISITOR_NAME = JavaRecursiveElementVisitor.class.getName();

  private static final @NonNls String EMPTY_LAMBDA = "public void visitLambdaExpression(PsiLambdaExpression expression) {}";
  private static final @NonNls String EMPTY_CLASS = "public void visitClass(PsiClass aClass) {}";

  @NotNull
  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
        super.visitClass(aClass);
        if (InheritanceUtil.isInheritor(aClass, true, BASE_WALKING_VISITOR_NAME) ||
            InheritanceUtil.isInheritor(aClass, true, BASE_VISITOR_NAME)) {
          if (findVisitMethod(aClass, "visitReturnStatement", PsiReturnStatement.class.getName())) {
            final boolean skipLambdaFound = findVisitMethod(aClass, "visitLambdaExpression", PsiLambdaExpression.class.getName());
            final boolean skipClassFound = findVisitMethod(aClass, "visitClass", PsiClass.class.getName());
            if (!(skipClassFound && skipLambdaFound)) {

              final String[] methods;
              final String name;
              if (!skipLambdaFound ^ !skipClassFound) {
                if (!skipLambdaFound) {
                  name = DevKitBundle.message("inspections.unsafe.return.insert.visit.lambda.expression");
                  methods = new String[]{EMPTY_LAMBDA};
                }
                else {
                  name = DevKitBundle.message("inspections.unsafe.return.insert.visit.class.method");
                  methods = new String[]{EMPTY_CLASS};
                }
              }
              else {
                name = DevKitBundle.message("inspections.unsafe.return.insert.visit.lambda.expression.and.class.methods");
                methods = new String[]{EMPTY_LAMBDA, EMPTY_CLASS};
              }
              holder.registerProblem(aClass,
                                     HighlightNamesUtil.getClassDeclarationTextRange(aClass).shiftRight(-aClass.getTextRange().getStartOffset()),
                                     DevKitBundle.message("inspections.unsafe.return.message"),
                                     new MySkipVisitFix(name, methods));
            }
          }
        }
      }
    };
  }

  private static boolean findVisitMethod(PsiClass aClass, String visitMethodName, String argumentType) {
    final PsiMethod[] visitReturnStatements = aClass.findMethodsByName(visitMethodName, false);
    for (PsiMethod method : visitReturnStatements) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 1 && parameters[0].getType().equalsToText(argumentType)) {
        return true;
      }
    }
    return false;
  }

  private static class MySkipVisitFix implements LocalQuickFix {
    private final @IntentionName String myName;
    private final String[] myMethods;

    MySkipVisitFix(@IntentionName String name, String[] methods) {
      myName = name;
      myMethods = methods;
    }

    @IntentionName
    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return DevKitBundle.message("inspections.unsafe.return.insert.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiClass) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        final PsiClass aClass = (PsiClass)element;
        for (String methodText : myMethods) {
          final PsiMethod method = factory.createMethodFromText(methodText, element);
          PsiMethod overridden = aClass.findMethodBySignature(method, true);
          if (overridden != null) {
            OverrideImplementUtil.annotateOnOverrideImplement(method, aClass, overridden);
          }
          aClass.add(method);
        }
      }
    }
  }
}
