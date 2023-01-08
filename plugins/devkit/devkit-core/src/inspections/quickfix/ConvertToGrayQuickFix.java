// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.Gray;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;
import org.jetbrains.uast.generate.UastElementFactory;

/**
 * @author Konstantin Bulenkov
 */
public class ConvertToGrayQuickFix implements LocalQuickFix {
  private final int myGrayValue;

  public ConvertToGrayQuickFix(int grayValue) {
    myGrayValue = grayValue;
  }

  @Override
  public @IntentionName @NotNull String getName() {
    return DevKitBundle.message("inspections.use.gray.fix.convert.name", myGrayValue);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return DevKitBundle.message("inspections.use.gray.fix.convert.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    UCallExpression awtGrayColorConstructor = UastContextKt.toUElement(element, UCallExpression.class);
    if (awtGrayColorConstructor == null) return;
    UastCodeGenerationPlugin generationPlugin = UastCodeGenerationPlugin.byLanguage(element.getLanguage());
    if (generationPlugin == null) return;
    UastElementFactory pluginElementFactory = generationPlugin.getElementFactory(project);
    String grayConstant = Gray.class.getName() + "._" + myGrayValue;
    UQualifiedReferenceExpression grayConstantReference = pluginElementFactory.createQualifiedReference(grayConstant, element);
    if (grayConstantReference == null) return;
    generationPlugin.replace(awtGrayColorConstructor, grayConstantReference, UQualifiedReferenceExpression.class);
  }
}
