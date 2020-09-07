// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

/**
 * @author Konstantin Bulenkov
 */
public class UseCoupleQuickFix implements LocalQuickFix {
  private static final String COUPLE_FQN = "com.intellij.openapi.util.Couple";

  @IntentionName
  private final String myText;

  public UseCoupleQuickFix(@IntentionName String text) {
    myText = text;
  }

  @Override
  public @IntentionName @NotNull String getName() {
    return myText;
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return DevKitBundle.message("inspections.use.couple.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiElement newElement;
    if (element instanceof PsiTypeElement) {
      final PsiTypeElement typeElement = (PsiTypeElement)element;
      final PsiClassType type1 = (PsiClassType)typeElement.getType();
      final PsiType[] parameters = type1.getParameters();
      if (parameters.length != 2) {
        return;
      }
      final PsiTypeElement newType = factory.createTypeElementFromText(COUPLE_FQN + "<" + parameters[0].getCanonicalText() + ">", element.getContext());
      newElement = element.replace(newType);
    }
    else {
      final PsiElement parent = element.getParent().getParent();
      if (!(parent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)parent;
      final String text = COUPLE_FQN + ".of" + methodCallExpression.getArgumentList().getText();
      final PsiExpression expression = factory.createExpressionFromText(text, element.getContext());
      newElement = parent.replace(expression);
    }
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
  }
}
