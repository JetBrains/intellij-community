// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.deprecation.DeprecationInspectionBase;
import com.intellij.ide.presentation.Presentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

public class PresentationAnnotationInspection extends DevKitUastInspectionBase {

  public PresentationAnnotationInspection() {
    super(UClass.class);
  }

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull UClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
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
      final PsiElement resolve = reference.resolve();
      if (resolve == null) {
        holder.registerProblem(reference);
      }
      else {
        assert resolve instanceof PsiField;
        DeprecationInspectionBase.checkDeprecated((PsiField)resolve, iconExpressionPsi, reference.getRangeInElement(), false,
                                                  false, true, false,
                                                  holder, false, false, ProblemHighlightType.LIKE_DEPRECATED);
      }
    }
    return holder.getResultsArray();
  }
}
