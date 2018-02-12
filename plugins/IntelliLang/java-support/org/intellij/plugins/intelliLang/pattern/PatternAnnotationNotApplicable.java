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
package org.intellij.plugins.intelliLang.pattern;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.intellij.plugins.intelliLang.util.RemoveAnnotationFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;

public class PatternAnnotationNotApplicable extends LocalInspectionTool {
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public String getGroupDisplayName() {
    return PatternValidator.PATTERN_VALIDATION;
  }

  @NotNull
  public String getDisplayName() {
    return "Pattern Annotation not applicable";
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      final String annotationName = Configuration.getProjectInstance(holder.getProject()).getAdvancedConfiguration().getPatternAnnotationClass();

      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
        final String name = annotation.getQualifiedName();
        if (annotationName.equals(name)) {
          checkAnnotation(annotation, holder);
        }
        else if (name != null) {
          final PsiClass psiClass = JavaPsiFacade.getInstance(annotation.getProject()).findClass(name, annotation.getResolveScope());
          if (psiClass != null && AnnotationUtil.isAnnotated(psiClass, annotationName, CHECK_EXTERNAL)) {
            checkAnnotation(annotation, holder);
          }
        }
      }
    };
  }

  private void checkAnnotation(PsiAnnotation annotation, ProblemsHolder holder) {
    final PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class);
    if (owner instanceof PsiVariable) {
      final PsiType type = ((PsiVariable)owner).getType();
      if (!PsiUtilEx.isString(type)) {
        registerProblem(annotation, holder);
      }
    }
    else if (owner instanceof PsiMethod) {
      final PsiType type = ((PsiMethod)owner).getReturnType();
      if (type != null && !PsiUtilEx.isString(type)) {
        registerProblem(annotation, holder);
      }
    }
  }

  private void registerProblem(PsiAnnotation annotation, ProblemsHolder holder) {
    holder.registerProblem(annotation, "Pattern Annotation is only applicable to elements of type String", new RemoveAnnotationFix(this));
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "PatternNotApplicable";
  }
}