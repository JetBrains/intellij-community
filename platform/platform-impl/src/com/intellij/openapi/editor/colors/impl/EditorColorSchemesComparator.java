// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;

import static com.intellij.openapi.editor.colors.EditorColorsScheme.DEFAULT_SCHEME_NAME;

/**
 * Defines the order in which editor color schemes are displayed.
 */
public final class EditorColorSchemesComparator implements Comparator<EditorColorsScheme> {
  public final static EditorColorSchemesComparator INSTANCE = new EditorColorSchemesComparator();

  public final static int DEFAULT_SCHEME          = 0;
  public final static int ONE_OF_DEFAULT_SCHEMES  = DEFAULT_SCHEME + 1;
  public final static int BUNDLED_SCHEME          = ONE_OF_DEFAULT_SCHEMES + 1;
  public final static int CUSTOM_SCHEME           = BUNDLED_SCHEME + 1;

  private EditorColorSchemesComparator() {
  }

  @Override
  public int compare(EditorColorsScheme s1, EditorColorsScheme s2) {
    int order1 = getOrder(s1);
    int order2 = getOrder(s2);
    if (order1 != order2) return order1 - order2;
    return s1.getName().compareToIgnoreCase(s2.getName());
  }


  private static int getOrder(@NotNull EditorColorsScheme scheme) {
    if (scheme instanceof AbstractColorsScheme) {
      EditorColorsScheme original = ((AbstractColorsScheme)scheme).getOriginal();
      if (original instanceof DefaultColorsScheme) {
        return DEFAULT_SCHEME_NAME.equals(original.getName()) ? DEFAULT_SCHEME : ONE_OF_DEFAULT_SCHEMES;
      }
      if (original instanceof ReadOnlyColorsScheme) {
        if (ExperimentalUI.isNewUI()) {
          int i = Arrays.asList("High contrast", "Dark", "Light").indexOf(original.getName());
          if (i >= 0) {
            return -(i + 1);
          }
        }
        return BUNDLED_SCHEME;
      }
    }
    return CUSTOM_SCHEME;
  }
}
