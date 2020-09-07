// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

/**
 * @author Konstantin Bulenkov
 */
public class ChangeToPairCreateQuickFix implements LocalQuickFix {
  
  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return DevKitBundle.message("inspections.dont.use.new.pair.change.to.pair.fix");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement().getParent();
    if (!(element instanceof PsiNewExpression)) {
      return;
    }
    final PsiNewExpression newExpression = (PsiNewExpression)element;
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) {
      return;
    }
    String newText = "com.intellij.openapi.util.Pair.create" + argumentList.getText();
    PsiExpression expression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(newText, element.getContext());
    PsiElement newElement = element.replace(expression);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
  }
}
