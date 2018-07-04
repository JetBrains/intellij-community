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

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.presentation.Presentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

public class PresentationAnnotationInspection extends DevKitUastInspectionBase {

  @Nullable
  @Override
  public ProblemDescriptor[] checkClass(@NotNull UClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final UAnnotation annotation = aClass.findAnnotation(Presentation.class.getCanonicalName());
    if (annotation == null) {
      return null;
    }

    UExpression iconExpression = annotation.findDeclaredAttributeValue("icon");
    if (!(iconExpression instanceof ULiteralExpression)) {
      return null;
    }

    PsiElement iconExpressionPsi = UastLiteralUtils.getPsiLanguageInjectionHost((ULiteralExpression)iconExpression);
    if (iconExpressionPsi == null) {
      return null;
    }

    ProblemsHolder holder = new ProblemsHolder(manager, iconExpressionPsi.getContainingFile(), isOnTheFly);
    PsiReference[] references = iconExpressionPsi.getReferences();
    for (PsiReference reference : references) {
      if (reference.resolve() == null) {
        holder.registerProblem(reference);
      }
    }
    return holder.getResultsArray();
  }
}
