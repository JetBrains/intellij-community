// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;
import org.jetbrains.idea.devkit.inspections.quickfix.UseCoupleQuickFix;

/**
 * @author Konstantin Bulenkov
 */
public class UseCoupleInspection extends DevKitInspectionBase {
  private static final String PAIR_FQN = "com.intellij.openapi.util.Pair";

  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitTypeElement(PsiTypeElement typeElement) {
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
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        if ("create".equals(methodExpression.getReferenceName())) {
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

}
