// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.json.psi.*;
import com.intellij.model.SymbolResolveResult;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.impl.JsonSchemaBaseReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Konstantin Bulenkov
 */
public class ThemeJsonNamedColorPsiReference extends JsonSchemaBaseReference<JsonLiteral> {
  private final String myName;

  public ThemeJsonNamedColorPsiReference(JsonLiteral element) {
    super(element, element.getTextLength() >= 2 ? new TextRange(1, element.getTextLength() - 1) : TextRange.EMPTY_RANGE);
    myName = StringUtil.unquoteString(element.getText());
  }

  @Nullable
  @Override
  public PsiElement resolveInner() {
    PsiFile file = getElement().getContainingFile();
    if (file instanceof JsonFile) {
      PsiElement object = file.getFirstChild();
      if (object instanceof JsonObject) {
        PsiElement[] children = object.getChildren();
        for (PsiElement child : children) {
          if (child instanceof JsonProperty && ((JsonProperty)child).getName().equals("colors")) {
            JsonValue colors = ((JsonProperty)child).getValue();
            if (colors != null) {
              for (PsiElement namedColor: colors.getChildren()) {
                if (namedColor instanceof JsonProperty && ((JsonProperty)namedColor).getName().equals(myName)) {
                  return ((JsonProperty)namedColor).getNameElement();
                }
              }
            }
          }
        }
      }
    }

    return null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public Collection<? extends SymbolResolveResult> resolveReference() {
    return Collections.emptyList();
  }
}
