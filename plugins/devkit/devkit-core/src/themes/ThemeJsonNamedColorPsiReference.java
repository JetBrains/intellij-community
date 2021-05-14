// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonLiteral;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.impl.JsonSchemaBaseReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
class ThemeJsonNamedColorPsiReference extends JsonSchemaBaseReference<JsonLiteral> {
  private final String myName;

  ThemeJsonNamedColorPsiReference(JsonLiteral element) {
    super(element, element.getTextLength() >= 2 ? new TextRange(1, element.getTextLength() - 1) : TextRange.EMPTY_RANGE);
    myName = StringUtil.unquoteString(element.getText());
  }

  @Nullable
  @Override
  public PsiElement resolveInner() {
    final PsiFile containingFile = getElement().getContainingFile();
    if (!(containingFile instanceof JsonFile)) return null;

    final List<JsonProperty> namedColors = ThemeJsonUtil.getNamedColors((JsonFile)containingFile);
    return ContainerUtil.find(namedColors, property -> property.getName().equals(myName));
  }

  @Override
  public Object @NotNull [] getVariants() {
    return EMPTY_ARRAY;
  }
}
