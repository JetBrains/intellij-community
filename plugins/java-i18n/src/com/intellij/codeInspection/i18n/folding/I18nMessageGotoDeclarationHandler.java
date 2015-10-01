/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.i18n.folding;

import com.intellij.codeInsight.folding.impl.EditorFoldingInfo;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.CompositeFoldingBuilder;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class I18nMessageGotoDeclarationHandler extends GotoDeclarationHandlerBase {
  private static final Key<FoldingBuilder> KEY = CompositeFoldingBuilder.FOLDING_BUILDER;

  @Override
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement element, Editor editor) {
    if (!(element instanceof PsiJavaToken)) return null;

    int i = 4; //some street magic
    while (element != null && i > 0) {
      final ASTNode node = element.getNode();
      if (node != null && node.getUserData(KEY) instanceof PropertyFoldingBuilder) {
        break;
      }
      else {
        i--;
        element = element.getParent();
      }
    }

    //case: "literalAnnotatedWithPropertyKey"
    if (element instanceof PsiLiteralExpression) {
      return resolve(element);
    }

    //case: MyBundle.message("literalAnnotatedWithPropertyKey", param1, param2)
    if (element instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;
      FoldRegion foldRegion = null;
      for (FoldRegion region : editor.getFoldingModel().getAllFoldRegions()) {
        final PsiElement psiElement = EditorFoldingInfo.get(editor).getPsiElement(region);
        if (methodCall.equals(psiElement)) {
          foldRegion = region;
        }
      }

      if (foldRegion == null || foldRegion.isExpanded()) return null;

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
