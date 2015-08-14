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
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.references.IdeaModuleReference;
import org.jetbrains.idea.devkit.references.RuntimeModuleReferenceBase;

/**
 * @author nik
 */
public class RuntimeModuleJavaReferencesInspection extends BaseJavaLocalInspectionTool {
  protected static void checkReferences(PsiLiteral literal, @NotNull ProblemsHolder holder) {
    for (PsiReference reference : literal.getReferences()) {
      if (reference instanceof RuntimeModuleReferenceBase && reference.resolve() == null) {
        RuntimeModuleReferenceBase moduleReference = (RuntimeModuleReferenceBase)reference;
        String kind = moduleReference instanceof IdeaModuleReference ? "module" : "library";
        holder.registerProblem(literal, "Cannot resolve runtime " + kind + " '" + moduleReference.getValue() + "'", ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, reference.getRangeInElement());
      }
    }
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        checkReferences(expression, holder);
      }
    };
  }
}
