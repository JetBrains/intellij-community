/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.references.RuntimeModuleReference;

/**
 * @author nik
 */
public class RuntimeModuleReferencesInspection extends BaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        for (PsiReference reference : expression.getReferences()) {
          if (reference instanceof RuntimeModuleReference && reference.resolve() == null) {
            RuntimeModuleReference moduleReference = (RuntimeModuleReference)reference;
            holder.registerProblem(expression, "Cannot resolve runtime module '" + moduleReference.getValue() + "'", ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                                   reference.getRangeInElement());
          }
        }
      }
    };
  }
}
