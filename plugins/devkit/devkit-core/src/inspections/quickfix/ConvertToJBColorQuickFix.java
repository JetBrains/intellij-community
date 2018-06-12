/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class ConvertToJBColorQuickFix extends LocalQuickFixBase {
  public ConvertToJBColorQuickFix() {
    super("Convert to JBColor");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final String newJBColor = String.format("new %s(%s, new java.awt.Color())", JBColor.class.getName(), element.getText());
    final PsiExpression expression = factory.createExpressionFromText(newJBColor, element.getContext());
    final PsiElement newElement = element.replace(expression);
    final PsiElement el = JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
    final int offset = el.getTextOffset() + el.getText().length() - 2;
    final Editor editor = PsiUtilBase.findEditor(el);
    if (editor != null) {
      editor.getCaretModel().moveToOffset(offset);
    }
  }
}
