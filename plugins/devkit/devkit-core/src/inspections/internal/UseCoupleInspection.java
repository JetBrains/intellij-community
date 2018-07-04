/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
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
        if ((type instanceof PsiClassType)) {
          final PsiClassType classType = (PsiClassType)type;
          final String canonicalText = classType.rawType().getCanonicalText();
          if (PAIR_FQN.equals(canonicalText)) {
            final PsiType[] parameters = classType.getParameters();
            if (parameters.length == 2 && parameters[0].equals(parameters[1])) {
                final String name = "Replace with 'Couple<" + parameters[0].getPresentableText() + ">'";
                holder.registerProblem(typeElement, name, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new UseCoupleQuickFix(name));
            }
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
                  final String name = "Replace with 'Couple.of()'";
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
