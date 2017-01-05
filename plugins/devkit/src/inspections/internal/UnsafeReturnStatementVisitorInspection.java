/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightNamesUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;

public class UnsafeReturnStatementVisitorInspection extends DevKitInspectionBase {

  private static final String BASE_WALKING_VISITOR_NAME = JavaRecursiveElementWalkingVisitor.class.getName();
  private static final String BASE_VISITOR_NAME = JavaRecursiveElementVisitor.class.getName();
  
  private static final String EMPTY_LAMBDA = "public void visitLambdaExpression(PsiLambdaExpression expression) {}";
  private static final String EMPTY_CLASS  = "public void visitClass(PsiClass aClass) {}";

  @NotNull
  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
        super.visitClass(aClass);
        if (InheritanceUtil.isInheritor(aClass, true, BASE_WALKING_VISITOR_NAME) ||
            InheritanceUtil.isInheritor(aClass, true, BASE_VISITOR_NAME)) {
          if (findVisitMethod(aClass, "visitReturnStatement", PsiReturnStatement.class.getName()) ) {
            final boolean skipLambdaFound = findVisitMethod(aClass, "visitLambdaExpression", PsiLambdaExpression.class.getName());
            final boolean skipClassFound = findVisitMethod(aClass, "visitClass", PsiClass.class.getName());
            if (!(skipClassFound && skipLambdaFound)) {
              
              final String[] methods;
              final String name;
              if (!skipLambdaFound ^ !skipClassFound) {
                if (!skipLambdaFound) {
                  name = "Insert visitLambdaExpression method";
                  methods = new String[]{EMPTY_LAMBDA};
                } else {
                  name = "Insert visitClass method";
                  methods = new String[]{EMPTY_CLASS};
                }
              }
              else {
                name = "Insert visitLambdaExpression/visitClass methods";
                methods = new String[]{EMPTY_LAMBDA, EMPTY_CLASS};
              }
              holder.registerProblem(aClass, HighlightNamesUtil.getClassDeclarationTextRange(aClass).shiftRight(-aClass.getTextRange().getStartOffset()),
                                     "Recursive visitors which visit return statements most probably should specifically process anonymous/local classes as well as lambda expressions",
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
    private final String myName;
    private final String[] myMethods;

    public MySkipVisitFix(String name, String[] methods) {
      myName = name;
      myMethods = methods;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Skip anonymous/local classes";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiClass) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        for (String method : myMethods) {
          element.add(factory.createMethodFromText(method, element));
        }
      }
    }
  }
}
