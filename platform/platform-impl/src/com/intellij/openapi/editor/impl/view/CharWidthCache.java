// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import gnu.trove.TIntFloatHashMap;
import org.intellij.lang.annotations.JdkConstants;

/**
 * Cache of char widths for different font styles
 */
class CharWidthCache {
  private static final int CACHE_SIZE_LIMIT = 1024;
  private static final float SHIFT = 1f;

  private final EditorView myView;
  private final TIntFloatHashMap myCache = new TIntFloatHashMap();

  CharWidthCache(EditorView view) {myView = view;}

  void clear() {
    myCache.clear();
  }

  float getCodePointWidth(int codePoint, @JdkConstants.FontStyle int fontStyle) {
    int key = createKey(codePoint, fontStyle);
    float width = getCachedValue(key);
    if (width < 0) {
      width = ComplementaryFontsRegistry.getFontAbleToDisplay(codePoint, fontStyle,
                                                              myView.getEditor().getColorsScheme().getFontPreferences(),
                                                              myView.getFontRenderContext()).charWidth2D(codePoint);
      saveInCache(key, width);
    }
    return width;
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
    if (myCache.size() >= CACHE_SIZE_LIMIT) myCache.clear();
    myCache.put(key, value + SHIFT);
  }

  private static int createKey(int codePoint, @JdkConstants.FontStyle int fontStyle) {
    return (fontStyle << 21) | codePoint;
  }
}
