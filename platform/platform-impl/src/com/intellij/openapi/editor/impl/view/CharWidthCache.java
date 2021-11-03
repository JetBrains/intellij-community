// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import org.intellij.lang.annotations.JdkConstants;

/**
 * Cache of char widths for different font styles
 */
final class CharWidthCache {
  private static final int CACHE_SIZE_LIMIT = 1024;
  private static final float SHIFT = 1f;

  private final EditorView myView;
  private final Int2FloatOpenHashMap myCache = new Int2FloatOpenHashMap();

  CharWidthCache(EditorView view) {
    myView = view;
  }

  void clear() {
    myCache.clear();
  }

  float getCodePointWidth(int codePoint, @JdkConstants.FontStyle int fontStyle) {
    int key = createKey(codePoint, fontStyle);
    float width = getCachedValue(key);
    if (width < 0) {
      width = calcValue(codePoint, fontStyle);
      saveInCache(key, width);
    }
    return width;
  }

  private float calcValue(int codePoint, int fontStyle) {
    Editor editor = myView.getEditor();
    if (editor.getSettings().isShowingSpecialChars()) {
      // This is a simplification - we don't account for special characters not rendered in certain circumstances(based on surrounding
      // characters), so a premature wrapping can occur sometimes (as the representation using Unicode name is most certainly wider than the
      // original character).
      SpecialCharacterFragment specialCharacterFragment = SpecialCharacterFragment.create(myView, codePoint, null, 0);
      if (specialCharacterFragment != null) {
        return specialCharacterFragment.visualColumnToX(0, 1);
      }
    }
    return ComplementaryFontsRegistry.getFontAbleToDisplay(codePoint, fontStyle, editor.getColorsScheme().getFontPreferences(),
                                                           myView.getFontRenderContext()).charWidth2D(codePoint);
  }

  /**
   * @return a negative value, if there's no value in cache
   */
  private float getCachedValue(int key) {
    return myCache.get(key) - SHIFT;
  }

  /**
   * @param value assumed to be non-negative
   */
  private void saveInCache(int key, float value) {
    if (myCache.size() >= CACHE_SIZE_LIMIT) {
      myCache.clear();
    }
    myCache.put(key, value + SHIFT);
  }

  private static int createKey(int codePoint, @JdkConstants.FontStyle int fontStyle) {
    return (fontStyle << 21) | codePoint;
  }
}
