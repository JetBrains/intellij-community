// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n.folding;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class I18nMessageGotoDeclarationHandler extends GotoDeclarationHandlerBase {

  @Override
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement element, Editor editor) {
    if (!(element instanceof PsiJavaToken)) return null;
    FoldRegion region = editor.getFoldingModel().getCollapsedRegionAtOffset(element.getTextRange().getStartOffset());
    if (region == null) return null;

    PsiElement editableElement = EditPropertyValueAction.getEditableElement(region);
    //case: "literalAnnotatedWithPropertyKey"
    if (editableElement instanceof PsiLiteralExpression) {
      return resolve(editableElement);
    }

    //case: MyBundle.message("literalAnnotatedWithPropertyKey", param1, param2)
    if (editableElement instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)editableElement;
      for (PsiExpression expression : methodCall.getArgumentList().getExpressions()) {
        if (expression instanceof PsiLiteralExpression && PropertyFoldingBuilder.isI18nProperty((PsiLiteralExpression)expression)) {
          return resolve(expression);
        }
      }
    }

    return null;
  }

  @Nullable
  private static PsiElement resolve(PsiElement element) {
    if (element == null) return null;
    final PsiReference[] references = element.getReferences();
    return references.length == 0 ? null : references[0].resolve();
  }
}
