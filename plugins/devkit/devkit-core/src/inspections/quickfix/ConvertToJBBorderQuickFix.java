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
public class ConvertToJBBorderQuickFix implements LocalQuickFix {
  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return DevKitBundle.message("inspections.use.dpi.aware.empty.border.convert.fix.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiCall newExpression = (PsiCall)descriptor.getPsiElement();
    PsiExpressionList list = newExpression.getArgumentList();
    @NonNls String text;
    if (list != null && list.getExpressionCount() == 4) {
      String top = list.getExpressions()[0].getText();
      String left = list.getExpressions()[1].getText();
      String bottom = list.getExpressions()[2].getText();
      String right = list.getExpressions()[3].getText();

      if (isZero(top, left, bottom, right)) {
        text = "empty()";
      }
      else if (isZero(left, bottom, right)) {
        text = "emptyTop(" + top + ")";
      }
      else if (isZero(top, bottom, right)) {
        text = "emptyLeft(" + left + ")";
      }
      else if (isZero(top, left, right)) {
        text = "emptyBottom(" + bottom + ")";
      }
      else if (isZero(top, left, bottom)) {
        text = "emptyRight(" + right + ")";
      }
      else if (top.equals(left) && left.equals(bottom) && bottom.equals(right)) {
        text = "empty(" + top + ")";
      }
      else if (top.equals(bottom) && right.equals(left)) {
        text = String.format("empty(%s, %s)", top, left);
      }
      else {
        text = String.format("empty(%s, %s, %s, %s)", top, left, bottom, right);
      }

      text = JBUI.class.getName() + ".Borders." + text;

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

  public static boolean canSimplify(PsiMethodCallExpression expression) {
    PsiType[] types = expression.getArgumentList().getExpressionTypes();
    if (!(types.length == 1 || types.length == 2 || types.length == 4)) {
      return false;
    }
    for (PsiType type : types) {
      if (!PsiType.INT.equals(type)) {
        return false;
      }
    }

    PsiExpression[] params = expression.getArgumentList().getExpressions();
    if (params.length == 1) {
      return params[0].textMatches("0");
    }
    else if (params.length == 2) {
      return areSame(params);
    }
    else if (params.length == 4) {
      if (areSame(params) || (areSame(params[0], params[2]) && areSame(params[1], params[3]))) {
        return true;
      }
      int zeros = 0;
      for (PsiExpression param : params) zeros += isZero(param.getText()) ? 1 : 0;
      return zeros == 3;
    }
    return false;
  }

  private static boolean areSame(PsiExpression... params) {
    if (params.length < 2) return false;

    String gold = params[0].getText();
    for (int i = 1; i < params.length; i++) {
      if (!params[i].textMatches(gold)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isZero(String... args) {
    if (args.length == 0) return false;
    for (String arg : args) {
      if (!"0".equals(arg)) return false;
    }
    return true;
  }
}