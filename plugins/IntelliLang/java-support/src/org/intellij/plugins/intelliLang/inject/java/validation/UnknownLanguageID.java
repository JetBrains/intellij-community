/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.java.validation;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.Language;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.IntelliLangBundle;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.jetbrains.annotations.NotNull;

public class UnknownLanguageID extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      final String annotationName = Configuration.getProjectInstance(holder.getProject()).getAdvancedConfiguration().getLanguageAnnotationClass();

      @Override
      public void visitNameValuePair(@NotNull PsiNameValuePair valuePair) {
        final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(valuePair, PsiAnnotation.class);
        if (annotation != null) {
          final String qualifiedName = annotation.getQualifiedName();
          if (annotationName.equals(qualifiedName)) {
            final String name = valuePair.getName();
            if (name == null || "value".equals(name)) {
              final PsiAnnotationMemberValue value = valuePair.getValue();
              if (value instanceof PsiExpression expression) {
                final Object id = JavaPsiFacade.getInstance(expression.getProject()).
                  getConstantEvaluationHelper().computeConstantExpression(expression);
                if (id instanceof String) {
                  Language language = InjectorUtils.getLanguageByString((String)id);
                  if (language == null) {
                    holder.registerProblem(expression, IntelliLangBundle.message("inspection.unknown.language.ID.description", id), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
                  }
                }
              }
            }
          }
        }
      }
    };
  }

}
