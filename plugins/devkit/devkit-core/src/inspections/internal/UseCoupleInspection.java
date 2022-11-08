// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;

/**
 * @author Konstantin Bulenkov
 */
public class UseCoupleInspection extends DevKitInspectionBase {
  private static final String PAIR_FQN = "com.intellij.openapi.util.Pair";

  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
        super.visitTypeElement(typeElement);
        final PsiType type = typeElement.getType();
        if (PsiTypesUtil.classNameEquals(type, PAIR_FQN)) {
          final PsiClassType classType = (PsiClassType)type;
          final PsiType[] parameters = classType.getParameters();
          if (parameters.length == 2 && parameters[0].equals(parameters[1])) {
            final String name = DevKitBundle.message("inspections.use.couple.type", parameters[0].getPresentableText());
            holder.registerProblem(typeElement, name, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new UseCoupleQuickFix(name));
          }
        }
      }

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        String methodName = methodExpression.getReferenceName();
        if ("create".equals(methodName) || "pair".equals(methodName)) {
          final PsiMethod method = expression.resolveMethod();
          if (method != null) {
            final PsiClass psiClass = method.getContainingClass();
            if (psiClass != null && PAIR_FQN.equals(psiClass.getQualifiedName())) {
              final PsiType[] types = expression.getArgumentList().getExpressionTypes();
              if (types.length == 2 && types[0].equals(types[1])) {
                final PsiElement nameElement = methodExpression.getReferenceNameElement();
                if (nameElement != null) {
                  final String name = DevKitBundle.message("inspections.use.couple.of");
                  holder.registerProblem(nameElement, name, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new UseCoupleQuickFix(name));
                }
              }
            }
          }
        }
      }
    };
  }

  private static class UseCoupleQuickFix implements LocalQuickFix {
    private static final String COUPLE_FQN = "com.intellij.openapi.util.Couple";

    @IntentionName
    private final String myText;

    private UseCoupleQuickFix(@IntentionName String text) {
      myText = text;
    }

    @Override
    public @IntentionName @NotNull String getName() {
      return myText;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.use.couple.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiElement newElement;
      if (element instanceof PsiTypeElement) {
        final PsiTypeElement typeElement = (PsiTypeElement)element;
        final PsiClassType type1 = (PsiClassType)typeElement.getType();
        final PsiType[] parameters = type1.getParameters();
        if (parameters.length != 2) {
          return;
        }
        final PsiTypeElement newType =
          factory.createTypeElementFromText(COUPLE_FQN + "<" + parameters[0].getCanonicalText() + ">", element.getContext());
        newElement = element.replace(newType);
      }
      else {
        final PsiElement parent = element.getParent().getParent();
        if (!(parent instanceof PsiMethodCallExpression)) {
          return;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)parent;
        final String text = COUPLE_FQN + ".of" + methodCallExpression.getArgumentList().getText();
        final PsiExpression expression = factory.createExpressionFromText(text, element.getContext());
        newElement = parent.replace(expression);
      }
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
    }
  }
}
