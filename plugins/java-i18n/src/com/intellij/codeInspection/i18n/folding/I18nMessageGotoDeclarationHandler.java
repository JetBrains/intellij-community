// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n.folding;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.expressions.UInjectionHost;

/**
 * @author Konstantin Bulenkov
 */
public class I18nMessageGotoDeclarationHandler extends GotoDeclarationHandlerBase {

  @Override
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement element, Editor editor) {
    if (!(element instanceof LeafPsiElement)) return null;
    FoldRegion region = editor.getFoldingModel().getCollapsedRegionAtOffset(element.getTextRange().getStartOffset());
    if (region == null) return null;

    PsiElement editableElement = EditPropertyValueAction.getEditableElement(region);
    UElement uElement = UastContextKt.toUElement(editableElement);
    //case: "literalAnnotatedWithPropertyKey"
    if (uElement instanceof ULiteralExpression) {
      return JavaI18nUtil.resolveProperty((ULiteralExpression)uElement);
    }

    if (uElement instanceof UQualifiedReferenceExpression) {
      uElement = ((UQualifiedReferenceExpression)uElement).getSelector();
    }

    //case: MyBundle.message("literalAnnotatedWithPropertyKey", param1, param2)
    if (uElement instanceof UCallExpression call) {
      for (UExpression expression : call.getValueArguments()) {
        if (expression instanceof UInjectionHost injectionHost && PropertyFoldingBuilder.isI18nProperty(injectionHost)) {
          Property property = JavaI18nUtil.resolveProperty(expression);
          if (property != null) {
            return property;
          }
        }
      }
    }

    return null;
  }
}
