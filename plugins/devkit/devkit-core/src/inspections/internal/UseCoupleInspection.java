// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;

/**
 * @author Konstantin Bulenkov
 */
public class UseCoupleInspection extends DevKitInspectionBase {
  private static final String PAIR_FQN = Pair.class.getName();
  private static final String COUPLE_FQN = Couple.class.getName();

  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
        super.visitTypeElement(typeElement);
        String pairTypeName = getPairTypeParameterNameIfBothTheSame(typeElement);
        if (pairTypeName != null) {
          holder.registerProblem(typeElement,
                                 DevKitBundle.message("inspections.use.couple.type", pairTypeName),
                                 new UseCoupleTypeFix(pairTypeName));
        }
      }

      @Nullable
      private static String getPairTypeParameterNameIfBothTheSame(@NotNull PsiTypeElement typeElement) {
        PsiType type = typeElement.getType();
        if (PsiTypesUtil.classNameEquals(type, PAIR_FQN)) {
          PsiClassType classType = (PsiClassType)type;
          PsiType[] parameters = classType.getParameters();
          if (parameters.length == 2 && parameters[0].equals(parameters[1])) {
            return parameters[0].getPresentableText();
          }
        }
        return null;
      }

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        if (isPairFactoryMethodWithTheSameArgumentTypes(expression, methodExpression)) {
          PsiElement nameElement = methodExpression.getReferenceNameElement();
          if (nameElement != null) {
            holder.registerProblem(nameElement, DevKitBundle.message("inspections.use.couple.of"), new UseCoupleOfFactoryMethodFix());
          }
        }
      }

      private static boolean isPairFactoryMethodWithTheSameArgumentTypes(@NotNull PsiMethodCallExpression expression,
                                                                         @NotNull PsiReferenceExpression methodExpression) {
        String methodName = methodExpression.getReferenceName();
        if ("create".equals(methodName) || "pair".equals(methodName)) {
          PsiMethod method = expression.resolveMethod();
          if (method != null) {
            PsiClass psiClass = method.getContainingClass();
            if (psiClass != null && PAIR_FQN.equals(psiClass.getQualifiedName())) {
              PsiType[] types = expression.getArgumentList().getExpressionTypes();
              return types.length == 2 && types[0].equals(types[1]);
            }
          }
        }
        return false;
      }
    };
  }

  private static class UseCoupleOfFactoryMethodFix implements LocalQuickFix {

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      PsiElement parent = element.getParent().getParent();
      if (!(parent instanceof PsiMethodCallExpression)) {
        return;
      }
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)parent;
      String text = COUPLE_FQN + ".of" + methodCallExpression.getArgumentList().getText();
      PsiExpression expression = factory.createExpressionFromText(text, element.getContext());
      PsiElement newElement = parent.replace(expression);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
    }

    @Override
    public @IntentionName @NotNull String getName() {
      return DevKitBundle.message("inspections.use.couple.of");
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.use.couple.family.name");
    }
  }

  private static class UseCoupleTypeFix implements LocalQuickFix {

    private final String mySimpleTypeParameterName;

    UseCoupleTypeFix(String simpleTypeParameterName) {
      mySimpleTypeParameterName = simpleTypeParameterName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      if (element instanceof PsiTypeElement) {
        PsiTypeElement typeElement = (PsiTypeElement)element;
        PsiClassType type1 = (PsiClassType)typeElement.getType();
        PsiType[] parameters = type1.getParameters();
        if (parameters.length != 2) {
          return;
        }
        PsiTypeElement newType =
          factory.createTypeElementFromText(COUPLE_FQN + "<" + parameters[0].getCanonicalText() + ">", element.getContext());
        PsiElement newElement = element.replace(newType);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
      }
    }

    @Override
    public @IntentionName @NotNull String getName() {
      return DevKitBundle.message("inspections.use.couple.type", mySimpleTypeParameterName);
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.use.couple.family.name");
    }
  }
}
