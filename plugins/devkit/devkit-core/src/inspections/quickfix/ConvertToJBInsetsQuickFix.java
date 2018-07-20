/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class ConvertToJBInsetsQuickFix extends LocalQuickFixBase {
  public ConvertToJBInsetsQuickFix() {
    super("Convert to JBUI.insets(...)");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiNewExpression newExpression = (PsiNewExpression)descriptor.getPsiElement();
    PsiExpressionList list = newExpression.getArgumentList();
    String text = null;
    if ( list != null && list.getExpressionCount() == 4) {
      String top = list.getExpressions()[0].getText();
      String left = list.getExpressions()[1].getText();
      String bottom = list.getExpressions()[2].getText();
      String right = list.getExpressions()[3].getText();

      if (isZero(top, left, bottom, right)) {
        text = "emptyInsets()";
      } else if (isZero(left, bottom, right)) {
        text = "insetsTop(" + top + ")";
      } else if (isZero(top, bottom, right)) {
        text = "insetsLeft(" + left + ")";
      } else if (isZero(top, left, right)) {
        text = "insetsBottom(" + bottom + ")";
      } else if (isZero(top, left, bottom)) {
        text = "insetsRight(" + right + ")";
      } else if (top.equals(left) && left.equals(bottom) && bottom.equals(right) && right.equals(top)) {
        text = "insets(" + top + ")";
      } else if (top.equals(bottom) && right.equals(left)) {
        text = String.format("insets(%s, %s)", top, left);
      } else {
        text = String.format("insets(%s, %s, %s, %s)", top, left, bottom, right);
      }

      text = JBUI.class.getName() + "." + text;

      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiExpression expression = factory.createExpressionFromText(text, newExpression.getContext());
      final PsiElement newElement = newExpression.replace(expression);
      final PsiElement el = JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
      final int offset = el.getTextOffset() + el.getText().length() - 2;
      final Editor editor = PsiUtilBase.findEditor(el);
      if (editor != null) {
        editor.getCaretModel().moveToOffset(offset);
      }
    }
  }

  private static boolean isZero(String... args) {
    if (args.length == 0) return false;
    for (String arg : args) {
      if (!"0".equals(arg)) return false;
    }
    return true;
  }
}
