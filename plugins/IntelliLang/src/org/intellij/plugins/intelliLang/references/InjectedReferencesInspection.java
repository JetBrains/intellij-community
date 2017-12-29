/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.intellij.plugins.intelliLang.references;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class InjectedReferencesInspection extends LocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {

        PsiReference[] injected = InjectedReferencesContributor.getInjectedReferences(element);
        if (injected != null) {
          for (PsiReference reference : injected) {
            if (reference.resolve() == null) {
              TextRange range = reference.getRangeInElement();
              if (range.isEmpty() && range.getStartOffset() == 1 && "\"\"".equals(element.getText())) {
                String message = ProblemsHolder.unresolvedReferenceMessage(reference);
                holder.registerProblem(element, message, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, TextRange.create(0, 2));
              }
              else {
                holder.registerProblem(reference);
              }
            }
          }
        }

        super.visitElement(element);
      }
    };
  }
}
