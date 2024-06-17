// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;

import static com.intellij.openapi.editor.colors.EditorColorsScheme.getDefaultSchemeName;

/**
 * Defines the order in which editor color schemes are displayed.
 */
public final class EditorColorSchemesComparator implements Comparator<EditorColorsScheme> {
  public static final EditorColorSchemesComparator INSTANCE = new EditorColorSchemesComparator();

  public static final int DEFAULT_SCHEME          = 0;
  public static final int ONE_OF_DEFAULT_SCHEMES  = DEFAULT_SCHEME + 1;
  public static final int BUNDLED_NEW_UI_SCHEME   = ONE_OF_DEFAULT_SCHEMES + 1;
  public static final int BUNDLED_CLASSIC_SCHEME  = BUNDLED_NEW_UI_SCHEME + 1;
  public static final int CUSTOM_SCHEME           = BUNDLED_CLASSIC_SCHEME + 1;

  private EditorColorSchemesComparator() {
  }

  @Override
  public int compare(EditorColorsScheme s1, EditorColorsScheme s2) {
    int order1 = getOrder(s1, false);
    int order2 = getOrder(s2, false);
    if (order1 != order2) return order1 - order2;
    return s1.getName().compareToIgnoreCase(s2.getName());
  }

  public static int getGroupNumber(@NotNull EditorColorsScheme scheme) {
    return getOrder(scheme, true);
  }

  private static int getOrder(@NotNull EditorColorsScheme scheme, boolean groupNumberOnly) {
    if (scheme instanceof AbstractColorsScheme) {
      EditorColorsScheme original = ((AbstractColorsScheme)scheme).getOriginal();
      if (!groupNumberOnly && original instanceof DefaultColorsScheme) {
        return getDefaultSchemeName().equals(original.getName()) ? DEFAULT_SCHEME : ONE_OF_DEFAULT_SCHEMES;
      }
      if (original != null && original.isReadOnly()) {
        if (ExperimentalUI.isNewUI()) {
          int i = Arrays.asList("High contrast", "Dark", "Light").indexOf(original.getName());
          if (i >= 0) {
            return groupNumberOnly ? BUNDLED_NEW_UI_SCHEME : -(i + 1);
          }
        }
        return BUNDLED_CLASSIC_SCHEME;
      }
    }
    return CUSTOM_SCHEME;
  }
}
