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

import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.siyeh.ig.psiutils.CollectionUtils;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.IntelliLangBundle;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class LanguageMismatch extends LocalInspectionTool {
  public boolean CHECK_NON_ANNOTATED_REFERENCES = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("CHECK_NON_ANNOTATED_REFERENCES", IntelliLangBundle.message("flag.usages.of.non.annotated.elements"))
        .description(HtmlChunk.text(IntelliLangBundle.message("flag.usages.of.non.annotated.elements.description")))
    );
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      final Pair<String, ? extends Set<String>> annotationName = Configuration.getProjectInstance(holder.getProject()).getAdvancedConfiguration().getLanguageAnnotationPair();

      @Override
      public void visitExpression(@NotNull PsiExpression expression) {
        checkExpression(expression, holder, annotationName);
      }

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        if (expression.getParent() instanceof PsiMethodCallExpression) return;
        final PsiElement element = expression.resolve();
        if (!(element instanceof PsiModifierListOwner)) {
          return;
        }
        checkExpression(expression, holder, annotationName);
      }
    };
  }

  void checkExpression(PsiExpression expression, ProblemsHolder holder, Pair<String, ? extends Set<String>> annotationName) {
    final PsiType type = expression.getType();
    if (type == null || !PsiUtilEx.isStringOrStringArray(type)) {
      return;
    }

    final PsiModifierListOwner contextOwner = AnnotationUtilEx.getAnnotatedElementFor(expression, AnnotationUtilEx.LookupType.CONTEXT_ONLY);
    if (contextOwner != null && PsiUtilEx.isLanguageAnnotationTarget(contextOwner)) {
      final PsiAnnotation[] annotations = AnnotationUtilEx.getAnnotationFrom(contextOwner, annotationName, true);
      if (annotations.length > 0) {
        final String expected = AnnotationUtilEx.calcAnnotationValue(annotations, "value");
        if (expected != null) {
          final PsiModifierListOwner declOwner =
              AnnotationUtilEx.getAnnotatedElementFor(expression, AnnotationUtilEx.LookupType.PREFER_DECLARATION);
          if (declOwner != null && PsiUtilEx.isLanguageAnnotationTarget(declOwner)) {
            final PsiAnnotation[] as = AnnotationUtilEx.getAnnotationFrom(declOwner, annotationName, true);
            if (as.length > 0) {
              final String actual = AnnotationUtilEx.calcAnnotationValue(as, "value");
              if (!expected.equals(actual)) {
                // language annotation values from context and declaration don't match
                holder.registerProblem(expression, IntelliLangBundle.message("inspection.language.mismatch.description", expected, actual));
              }
            }
            else if (CHECK_NON_ANNOTATED_REFERENCES) {
              final PsiElement var =
                  PsiTreeUtil.getParentOfType(expression, PsiVariable.class, PsiExpressionList.class, PsiAssignmentExpression.class);
              // only nag about direct assignment or passing the reference as parameter
              if (var instanceof PsiVariable) {
                if (((PsiVariable)var).getInitializer() != expression) {
                  return;
                }
              }
              else if (var instanceof PsiExpressionList list) {
                if (!ArrayUtil.contains(expression, list.getExpressions())) {
                  return;
                }
              }
              else if (var instanceof PsiAssignmentExpression a) {
                if (a.getRExpression() != expression) {
                  return;
                }
              }
              if (declOwner instanceof PsiField && CollectionUtils.isConstantEmptyArray((PsiField)declOwner)) {
                // don't warn about unannotated empty array constants.
                return;
              }
              // context implies language, but declaration isn't annotated
              final PsiAnnotation annotation = annotations[annotations.length - 1];
              String fqn = Objects.requireNonNull(annotation.getQualifiedName());
              List<LocalQuickFix> fixes = new SmartList<>();
              PsiModifierListOwner owner = AnnotationUtilEx.getAnnotatedElementFor(expression, AnnotationUtilEx.LookupType.PREFER_DECLARATION);
              if (owner != null && AddAnnotationPsiFix.isAvailable(owner, fqn)) {
                fixes.add(new AddAnnotationFix(fqn, owner, annotation.getParameterList().getAttributes()));
              }
              holder.registerProblem(expression, IntelliLangBundle.message("inspection.language.problem.description", expected),
                                     fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
            }
          }
        }
      }
    }
  }

}
