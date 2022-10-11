// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;
import org.jetbrains.uast.generate.UastElementFactory;

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
    PsiElement element = descriptor.getPsiElement();
    UReferenceExpression awtColorConstantReference = getReferenceExpression(element);
    if (awtColorConstantReference == null) return;
    UastCodeGenerationPlugin generationPlugin = UastCodeGenerationPlugin.byLanguage(element.getLanguage());
    if (generationPlugin == null) return;
    UastElementFactory pluginElementFactory = generationPlugin.getElementFactory(project);
    String jbColorConstant = JBColor.class.getName() + '.' + buildColorConstantName(element);
    UQualifiedReferenceExpression jbColorConstantReference = pluginElementFactory.createQualifiedReference(jbColorConstant, element);
    if (jbColorConstantReference != null) {
      UQualifiedReferenceExpression replaced =
        generationPlugin.replace(awtColorConstantReference, jbColorConstantReference, UQualifiedReferenceExpression.class);
      if (replaced != null) {
        // it should be shortened automatically, but is not in tests, see: IDEA-303537
        generationPlugin.shortenReference(replaced);
      }
    }
  }

  @Nullable
  private static UReferenceExpression getReferenceExpression(PsiElement element) {
    UReferenceExpression expression = UastContextKt.toUElement(element, UQualifiedReferenceExpression.class);
    if (expression == null) {
      expression = UastContextKt.toUElement(element, USimpleNameReferenceExpression.class);
    }
    return expression;
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
