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

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastContextKt;

public class PresentationAnnotationInspection extends DevKitUastInspectionBase {
  private static final Logger LOG = Logger.getInstance(PresentationAnnotationInspection.class);

  @NotNull
  @Override
  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
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

        PsiElement iconExpressionPsi = iconExpression.getPsi();
        if (iconExpressionPsi == null) {
          LOG.error("Unexpected null value of @Presentation#icon expression PSI: " + element);
          return;
        }

        PsiReference[] references = iconExpressionPsi.getReferences();
        for (PsiReference reference : references) {
          if (reference.resolve() == null) {
            holder.registerProblem(reference);
          }
        }
      }
    };
  }
}
