// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonLiteral;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.impl.JsonSchemaBaseReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ThemeJsonNamedColorPsiReference extends JsonSchemaBaseReference<JsonLiteral> {
  private final String myName;

  ThemeJsonNamedColorPsiReference(JsonLiteral element) {
    super(element, element.getTextLength() >= 2 ? new TextRange(1, element.getTextLength() - 1) : TextRange.EMPTY_RANGE);
    myName = StringUtil.unquoteString(element.getText());
  }

  @Override
  public @Nullable PsiElement resolveInner() {
    PsiFile containingFile = getElement().getContainingFile();
    if (!(containingFile instanceof JsonFile)) return null;

    var namedColors = ThemeJsonUtil.getNamedColorsMap((JsonFile)containingFile);
    var color = namedColors.get(myName);
    if (color == null) return null;

    return color.declaration().retrieve();
  }

  @Override
  public Object @NotNull [] getVariants() {
    return EMPTY_ARRAY;
  }
}
