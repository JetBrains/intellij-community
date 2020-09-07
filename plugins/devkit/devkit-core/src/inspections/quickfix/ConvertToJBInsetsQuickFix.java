// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

/**
 * @author Konstantin Bulenkov
 */
public class ConvertToJBInsetsQuickFix implements LocalQuickFix {
  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return DevKitBundle.message("inspections.use.dpi.aware.insets.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiNewExpression newExpression = (PsiNewExpression)descriptor.getPsiElement();
    PsiExpressionList list = newExpression.getArgumentList();
    @NonNls String text;
    if (list != null && list.getExpressionCount() == 4) {
      String top = list.getExpressions()[0].getText();
      String left = list.getExpressions()[1].getText();
      String bottom = list.getExpressions()[2].getText();
      String right = list.getExpressions()[3].getText();

      if (isZero(top, left, bottom, right)) {
        text = "emptyInsets()";
      }
      else if (isZero(left, bottom, right)) {
        text = "insetsTop(" + top + ")";
      }
      else if (isZero(top, bottom, right)) {
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
      final Editor editor = PsiEditorUtil.findEditor(el);
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
