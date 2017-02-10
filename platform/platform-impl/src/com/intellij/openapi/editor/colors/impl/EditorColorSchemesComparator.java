/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

import static com.intellij.openapi.editor.colors.EditorColorsScheme.DEFAULT_SCHEME_NAME;

/**
 * Defines the order in which editor color schemes are displayed.
 */
public class EditorColorSchemesComparator implements Comparator<EditorColorsScheme> {
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
        return BUNDLED_SCHEME;
      }
    }
    return CUSTOM_SCHEME;
  }
}
