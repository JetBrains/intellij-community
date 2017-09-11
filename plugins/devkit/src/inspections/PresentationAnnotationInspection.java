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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastContextKt;

public class PresentationAnnotationInspection extends LocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(PresentationAnnotationInspection.class);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        UAnnotation annotation = UastContextKt.toUElement(element, UAnnotation.class);
        if (annotation == null) {
          return;
        }
        if (!Presentation.class.getCanonicalName().equals(annotation.getQualifiedName())) {
          return;
        }

        UExpression iconExpression = annotation.findAttributeValue("icon");
        if (iconExpression == null) {
          return;
        }

        Object iconExpressionValue = iconExpression.evaluate();
        if (!(iconExpressionValue instanceof String)) {
          // may happen in the middle of typing
          return;
        }

        String iconPath = (String)iconExpressionValue;
        if (StringUtil.isEmpty(iconPath) || IconLoader.findIcon(iconPath, false) != null) {
          return;
        }

        PsiElement iconExpressionPsi = iconExpression.getPsi();
        if (iconExpressionPsi == null) {
          LOG.error("Unexpected null value of @Presentation#icon expression PSI: " + element);
          return;
        }

        PsiReference reference = iconExpressionPsi.getReference();
        String message;
        if (reference != null) {
          message = ProblemsHolder.unresolvedReferenceMessage(reference);
        }
        else { // shouldn't actually happen, but just in case
          message = CodeInsightBundle.message("error.cannot.resolve.default.message", iconPath);
        }
        TextRange range = iconExpressionPsi.getTextRange().shiftLeft(element.getTextRange().getStartOffset());
        holder.registerProblem(element, message, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, range);
      }
    };
  }
}
