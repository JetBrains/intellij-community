// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
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
  private final String myConstantName;

  public ConvertToJBColorConstantQuickFix(@NonNls String constantName) {
    myConstantName = constantName;
  }

  @Override
  public @IntentionName @NotNull String getName() {
    return DevKitBundle.message("inspections.awt.color.used.fix.use.jb.color.constant.fix", myConstantName);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return DevKitBundle.message("inspections.awt.color.used.fix.use.jb.color.constant.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final String jbColorConstant = String.format("%s.%s", JBColor.class.getName(), myConstantName);
    final PsiExpression expression = factory.createExpressionFromText(jbColorConstant, element.getContext());
    final PsiElement newElement = element.replace(expression);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
  }
}
