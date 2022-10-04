// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElementKt;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

import java.util.List;

public class UnsafeReturnStatementVisitorInspection extends DevKitUastInspectionBase {

  private static final @NonNls String BASE_WALKING_VISITOR_NAME = JavaRecursiveElementWalkingVisitor.class.getName();
  private static final @NonNls String BASE_VISITOR_NAME = JavaRecursiveElementVisitor.class.getName();

  private static final @NonNls String EMPTY_VISIT_LAMBDA_METHOD = "public void visitLambdaExpression(PsiLambdaExpression expression) {}";
  private static final @NonNls String EMPTY_VISIT_CLASS_METHOD = "public void visitClass(PsiClass aClass) {}";

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull UClass uClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    PsiClass aClass = uClass.getJavaPsi();
    if (InheritanceUtil.isInheritor(aClass, true, BASE_WALKING_VISITOR_NAME) ||
        InheritanceUtil.isInheritor(aClass, true, BASE_VISITOR_NAME)) {
      if (hasMethod(uClass, "visitReturnStatement", PsiReturnStatement.class.getName())) {
        final boolean visitLambdaMissing = !hasMethod(uClass, "visitLambdaExpression", PsiLambdaExpression.class.getName());
        final boolean visitClassMissing = !hasMethod(uClass, "visitClass", PsiClass.class.getName());
        if (visitLambdaMissing || visitClassMissing) {
          PsiElement classNameAnchor = UElementKt.getSourcePsiElement(uClass.getUastAnchor());
          if (classNameAnchor != null) {
            final ProblemsHolder holder = createProblemsHolder(uClass, manager, isOnTheFly);
            holder.registerProblem(classNameAnchor,
                                   DevKitBundle.message("inspections.unsafe.return.message"),
                                   createFixes(uClass, visitLambdaMissing, visitClassMissing));
            return holder.getResultsArray();
          }
        }
      }
    }
    return ProblemDescriptor.EMPTY_ARRAY;
  }

  private static boolean hasMethod(UClass uClass, String visitMethodName, String argumentType) {
    return ContainerUtil.exists(uClass.getMethods(),
                                uMethod -> visitMethodName.equals(uMethod.getName()) && hasSingleParameterOfType(uMethod, argumentType));
  }

  private static boolean hasSingleParameterOfType(UMethod uMethod, String argumentType) {
    final List<UParameter> parameters = uMethod.getUastParameters();
    return parameters.size() == 1 && parameters.get(0).getType().equalsToText(argumentType);
  }

  private static LocalQuickFix[] createFixes(@NotNull UClass uClass, boolean visitLambdaMissing, boolean visitClassMissing) {
    if (!uClass.getLang().is(JavaLanguage.INSTANCE)) return LocalQuickFix.EMPTY_ARRAY;
    final String fixName;
    final String[] methodsToInsert;
    if (visitLambdaMissing && visitClassMissing) {
      fixName = DevKitBundle.message("inspections.unsafe.return.insert.visit.lambda.expression.and.class.methods");
      methodsToInsert = new String[]{EMPTY_VISIT_LAMBDA_METHOD, EMPTY_VISIT_CLASS_METHOD};
    }
    else if (visitLambdaMissing) {
      fixName = DevKitBundle.message("inspections.unsafe.return.insert.visit.lambda.expression");
      methodsToInsert = new String[]{EMPTY_VISIT_LAMBDA_METHOD};
    }
    else {
      fixName = DevKitBundle.message("inspections.unsafe.return.insert.visit.class.method");
      methodsToInsert = new String[]{EMPTY_VISIT_CLASS_METHOD};
    }
    return new LocalQuickFix[]{new MySkipVisitFix(fixName, methodsToInsert)};
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
      PsiClass aClass = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
      if (aClass != null) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        for (String methodText : myMethods) {
          final PsiMethod method = factory.createMethodFromText(methodText, aClass);
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
