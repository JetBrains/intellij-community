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

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.util.AnnotateFix;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class PatternOverriddenByNonAnnotatedMethod extends LocalInspectionTool {

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public String getGroupDisplayName() {
    return PatternValidator.PATTERN_VALIDATION;
  }

  @NotNull
  public String getDisplayName() {
    return "Non-annotated Method overrides @Pattern Method";
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      final Pair<String, ? extends Set<String>> annotationName = Configuration.getProjectInstance(holder.getProject()).getAdvancedConfiguration().getPatternAnnotationPair();

      @Override
      public void visitMethod(PsiMethod method) {
        final PsiIdentifier psiIdentifier = method.getNameIdentifier();
        if (psiIdentifier == null || !PsiUtilEx.isLanguageAnnotationTarget(method)) {
          return;
        }

        final PsiAnnotation[] annotationFrom = AnnotationUtilEx.getAnnotationFrom(method, annotationName, true, false);
        if (annotationFrom.length == 0) {
          final PsiAnnotation[] annotationFromHierarchy = AnnotationUtilEx.getAnnotationFrom(method, annotationName, true, true);
          if (annotationFromHierarchy.length > 0) {
            final String annotationClassname = annotationFromHierarchy[annotationFromHierarchy.length - 1].getQualifiedName();
            final String argList = annotationFromHierarchy[annotationFromHierarchy.length - 1].getParameterList().getText();
            holder.registerProblem(psiIdentifier, "Non-annotated Method overrides @Pattern Method",
                                   new AnnotateFix(method, annotationClassname, argList));
          }
        }
      }
    };
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "PatternOverriddenByNonAnnotatedMethod";
  }
}
