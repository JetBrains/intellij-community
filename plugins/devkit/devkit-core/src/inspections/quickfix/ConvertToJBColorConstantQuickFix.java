// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

/**
 * @author Konstantin Bulenkov
 */
public class ConvertToJBColorConstantQuickFix implements LocalQuickFix {

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return DevKitBundle.message("inspections.awt.color.used.fix.use.jb.color.constant.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final String jbColorConstant = String.format("%s.%s", JBColor.class.getName(), buildColorConstantName(element));
    final PsiExpression expression = factory.createExpressionFromText(jbColorConstant, element.getContext());
    final PsiElement newElement = element.replace(expression);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
  }

  @NotNull
  private static @NonNls String buildColorConstantName(@NotNull PsiElement expression) {
    @NonNls String text = expression.getText();
    if (text.contains(".")) {
      text = text.substring(text.lastIndexOf('.'));
    }
    text = StringUtil.trimStart(text, ".");
    if (text.equalsIgnoreCase("lightGray")) {
      text = "LIGHT_GRAY";
    }
    else if (text.equalsIgnoreCase("darkGray")) {
      text = "DARK_GRAY";
    }
    return StringUtil.toUpperCase(text);
  }
}
