// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.json.psi.JsonLiteral;
import com.intellij.json.psi.JsonProperty;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
class ThemeJsonNamedColorPsiReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (!(element instanceof JsonLiteral)) return PsiReference.EMPTY_ARRAY;

    JsonLiteral literal = (JsonLiteral)element;
    PsiElement parent = literal.getParent();
    if (!(parent instanceof JsonProperty)) return PsiReference.EMPTY_ARRAY;

    JsonProperty property = (JsonProperty)parent;
    if (property.getValue() != literal) return PsiReference.EMPTY_ARRAY;

    return new PsiReference[]{new ThemeJsonNamedColorPsiReference(literal)};
  }
}
