/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.psi.PropertyUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class TitleCapitalizationInspection extends BaseJavaLocalInspectionTool {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return "Plugin DevKit";
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Incorrect dialog title capitalization";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "DialogTitleCapitalization";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        if ("setTitle".equals(methodExpression.getReferenceName())) {
          PsiMethod psiMethod = expression.resolveMethod();
          if (psiMethod == null) {
            return;
          }
          PsiClass containingClass = psiMethod.getContainingClass();
          if (containingClass == null || !"com.intellij.openapi.ui.DialogWrapper".equals(containingClass.getQualifiedName())) {
            return;
          }
          PsiExpression[] args = expression.getArgumentList().getExpressions();
          if (args.length == 0) {
            return;
          }
          if (!hasTitleCapitalization(args[0])) {
            holder.registerProblem(args [0], "Dialog titles should use title capitalization", ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
        } 
      }
    };
  }

  private static boolean hasTitleCapitalization(PsiExpression arg) {
    if (arg instanceof PsiLiteralExpression) {
      Object value = ((PsiLiteralExpression)arg).getValue();
      if (value instanceof String) {
        return hasTitleCapitalization((String) value);
      }
    }
    if (arg instanceof PsiMethodCallExpression) {
      PsiMethod psiMethod = ((PsiMethodCallExpression)arg).resolveMethod();
      PsiExpression returnValue = PropertyUtils.getGetterReturnExpression(psiMethod);
      if (returnValue != null) {
        return hasTitleCapitalization(returnValue);
      }
      PsiExpression[] args = ((PsiMethodCallExpression)arg).getArgumentList().getExpressions();
      if (args.length > 0) {
        PsiReference[] references = args[0].getReferences();
        for (PsiReference reference : references) {

        }
      }
    }
    return true;
  }

  private static boolean hasTitleCapitalization(String value) {
    return StringUtil.wordsToBeginFromUpperCase(value).equals(value);
  }
}
