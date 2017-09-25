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
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFixBase;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;

/**
* @author Konstantin Bulenkov
*/
public class UseCoupleQuickFix extends LocalQuickFixBase {
  private static final String COUPLE_FQN = "com.intellij.openapi.util.Couple";

  public UseCoupleQuickFix(String text) {
    super(text, "Replace 'Pair' with 'Couple'");
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
