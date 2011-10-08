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
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.psi.PropertyUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
          String titleValue = getTitleValue(args [0]);
          if (titleValue != null && !hasTitleCapitalization(titleValue)) {
            holder.registerProblem(args [0], "Dialog title '" + titleValue + "' is not properly capitalized. It should have title capitalization",
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
        } 
      }
    };
  }

  @Nullable
  private static String getTitleValue(PsiExpression arg) {
    if (arg instanceof PsiLiteralExpression) {
      Object value = ((PsiLiteralExpression)arg).getValue();
      if (value instanceof String) {
        return (String) value;
      }
    }
    if (arg instanceof PsiMethodCallExpression) {
      PsiMethod psiMethod = ((PsiMethodCallExpression)arg).resolveMethod();
      PsiExpression returnValue = PropertyUtils.getGetterReturnExpression(psiMethod);
      if (returnValue != null) {
        return getTitleValue(returnValue);
      }
      Property propertyArgument = getPropertyArgument((PsiMethodCallExpression)arg);
      if (propertyArgument != null) {
        return propertyArgument.getUnescapedValue();
      }
    }
    if (arg instanceof PsiReferenceExpression) {
      PsiElement result = ((PsiReferenceExpression)arg).resolve();
      if (result instanceof PsiVariable && ((PsiVariable)result).hasModifierProperty(PsiModifier.FINAL)) {
        return getTitleValue(((PsiVariable) result).getInitializer());
      }
    }
    return null;
  }

  @Nullable
  private static Property getPropertyArgument(PsiMethodCallExpression arg) {
    PsiExpression[] args = ((PsiMethodCallExpression)arg).getArgumentList().getExpressions();
    if (args.length > 0) {
      PsiReference[] references = args[0].getReferences();
      for (PsiReference reference : references) {
        if (reference instanceof PropertyReference) {
          ResolveResult[] resolveResults = ((PropertyReference)reference).multiResolve(false);
          if (resolveResults.length == 1 && resolveResults[0].isValidResult()) {
            PsiElement element = resolveResults[0].getElement();
            if (element instanceof Property) {
              return (Property) element;
            }
          }
        }
      }
    }
    return null;
  }

  private static boolean hasTitleCapitalization(String value) {
    return StringUtil.wordsToBeginFromUpperCase(value).equals(value);
  }
}
