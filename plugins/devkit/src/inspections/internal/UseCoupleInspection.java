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

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.quickfix.UseCoupleQuickFix;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class UseCoupleInspection extends InternalInspection {
  private static final String PAIR_FQN = "com.intellij.openapi.util.Pair";

  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitTypeElement(PsiTypeElement type) {
        final String canonicalText = type.getType().getCanonicalText();
        if (canonicalText.startsWith(PAIR_FQN)) {
          if (canonicalText.contains("<") && canonicalText.endsWith(">")) {
            String genericTypes = canonicalText.substring(canonicalText.indexOf('<') + 1, canonicalText.length() - 1);
            final List<String> types = StringUtil.split(genericTypes, ",");
            if (types.size() == 2 && StringUtil.equals(types.get(0), types.get(1))) {
              final List<String> parts = StringUtil.split(types.get(0), ".");
              String typeName = parts.get(parts.size() - 1);
              final String name = "Change to Couple<" + typeName + ">";
              holder.registerProblem(type, name, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new UseCoupleQuickFix(name));
            }
          }

        }
        super.visitTypeElement(type);
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (expression.getText().startsWith("Pair.create")) {
          final PsiReference reference = expression.getMethodExpression().getReference();
          if (reference != null) {
            final PsiElement method = reference.resolve();
            if (method instanceof PsiMethod) {
              final PsiClass psiClass = ((PsiMethod)method).getContainingClass();
              if (psiClass != null && PAIR_FQN.equals(psiClass.getQualifiedName())) {
                final PsiType[] types = expression.getArgumentList().getExpressionTypes();
                if (types.length == 2 && types[0].equals(types[1])) {
                  final String name = "Change to Couple.of";
                  holder.registerProblem(expression, name, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new UseCoupleQuickFix(name));
                }
              }
            }
          }
        }
        super.visitMethodCallExpression(expression);
      }
    };
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Use Couple<T> instead of Pair<T, T>";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "UseCouple";
  }
}
