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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import gnu.trove.TObjectIntHashMap;
import org.intellij.lang.annotations.JdkConstants;

import java.awt.*;
import java.awt.font.FontRenderContext;

/**
 * Not thread-safe. Performs caching of char widths, so cache reset must be invoked (via {@link #clearSymbolWidthCache()} method) when
 * font settings are changed in editor.
 *
 * @author Denis Zhdanov
 * @since Jul 27, 2010 4:06:27 PM
 */
public class DefaultEditorTextRepresentationHelper implements EditorTextRepresentationHelper {

  /**
   * We don't expect the user to have too many different font sizes and font types within the editor, however, need to
   * provide a defense from unlimited cache growing.
   */
  private static final int MAX_SYMBOLS_WIDTHS_CACHE_SIZE = 1000;

  /** We cache symbol widths here because it's detected to be a bottleneck. */
  private final TObjectIntHashMap<Key> mySymbolWidthCache = new TObjectIntHashMap<>();

  private final Key mySharedKey = new Key();

  /**
   * This is performance-related optimization because profiling shows that it's rather expensive to call
   * {@link Editor#getColorsScheme()} often due to contention in 'assert read access'.
   */
  private final Editor             myEditor;
  private FontRenderContext myFontRenderContext;

  public DefaultEditorTextRepresentationHelper(Editor editor) {
    myEditor = editor;
  }

  @Override
  public int charWidth(int c, int fontType) {
    // Symbol width retrieval is detected to be a bottleneck, hence, we perform a caching here in assumption that every representation
    // helper is editor-bound and cache size is not too big.
    mySharedKey.fontType = fontType;
    
    mySharedKey.c = c;
    return charWidth(c);
  }

  private int charWidth(int c) {
    int result = mySymbolWidthCache.get(mySharedKey);
    if (result > 0) {
      return result;
    }
    Key key = mySharedKey.clone();
    FontInfo font = ComplementaryFontsRegistry.getFontAbleToDisplay(c, key.fontType, myEditor.getColorsScheme().getFontPreferences(),
                                                                    myFontRenderContext);
    result = font.charWidth(c);
    if (mySymbolWidthCache.size() >= MAX_SYMBOLS_WIDTHS_CACHE_SIZE) {
      // Don't expect to be here.
      mySymbolWidthCache.clear();
    }
    mySymbolWidthCache.put(key, result);
    return result;
  }

  public void clearSymbolWidthCache() {
    mySymbolWidthCache.clear();
  }

  public void updateContext() {
    FontRenderContext oldContext = myFontRenderContext;
    myFontRenderContext = FontInfo.getFontRenderContext(myEditor.getContentComponent());
    if (!myFontRenderContext.equals(oldContext)) clearSymbolWidthCache();
  }

  private static class Key {
    @JdkConstants.FontStyle private int fontType;
    private int c;

    private Key() {
      this(Font.PLAIN, ' ');
    }

    Key(@JdkConstants.FontStyle int fontType, int c) {
      this.fontType = fontType;
      this.c = c;
    }

    @Override
    protected Key clone() {
      return new Key(fontType, c);
    }

    @Override
    public int hashCode() {
      int result = fontType;
      result = 31 * result + c;
      return result;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Key key = (Key)o;

      if (fontType != key.fontType) return false;
      if (c != key.c) return false;

      return true;
    }
  }
}
