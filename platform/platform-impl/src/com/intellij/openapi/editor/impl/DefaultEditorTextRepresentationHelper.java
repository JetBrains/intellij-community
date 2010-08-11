/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * Not thread-safe.
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
  private final TObjectIntHashMap<Key> mySymbolWidthCache = new TObjectIntHashMap<Key>();

  private final Key mySharedKey = new Key();

  /**
   * This is performance-related optimization because profiling shows that it's rather expensive to call
   * {@link Editor#getColorsScheme()} often due to contention in 'assert read access'.
   */
  private final EditorColorsScheme myColorsScheme;
  private final Editor             myEditor;

  public DefaultEditorTextRepresentationHelper(Editor editor) {
    myEditor = editor;
    myColorsScheme = myEditor.getColorsScheme();
  }

  @Override
  public int toVisualColumnSymbolsNumber(@NotNull CharSequence text, int start, int end, int x) {
    return EditorUtil.textWidthInColumns(myEditor, text, start, end, x);
  }

  @Override
  public int textWidth(@NotNull CharSequence text, int start, int end, int fontType, int x) {
    int startToUse = start;
    int lastLineFeedIndex = StringUtil.lastIndexOf(text, '\n', start, end);
    if (lastLineFeedIndex > 0) {
      startToUse = lastLineFeedIndex + 1;
    }
    int result = 0;

    // Symbol width retrieval is detected to be a bottleneck, hence, we perform a caching here in assumption that every representation
    // helper is editor-bound and cache size is not too big.
    mySharedKey.fontName = myColorsScheme.getEditorFontName();
    mySharedKey.fontSize = myColorsScheme.getEditorFontSize();
    mySharedKey.fontType = fontType;
    
    for (int i = startToUse; i < end; i++) {
      char c = text.charAt(i);
      if (c != '\t') {
        result += charWidth(c);
        continue;
      }

      result += EditorUtil.nextTabStop(x + result, myEditor) - result - x;
    }
    return result;
  }

  private int charWidth(char c) {
    int result = mySymbolWidthCache.get(mySharedKey);
    if (result > 0) {
      return result;
    }
    Key key = mySharedKey.clone();
    FontInfo font = ComplementaryFontsRegistry.getFontAbleToDisplay(c, key.fontSize, key.fontType, key.fontName);
    result = font.charWidth(c, myEditor.getContentComponent());
    if (mySymbolWidthCache.size() >= MAX_SYMBOLS_WIDTHS_CACHE_SIZE) {
      // Don't expect to be here.
      mySymbolWidthCache.clear();
    }
    mySymbolWidthCache.put(key, result);
    return result;
  }

  private static class Key {
    public String fontName;
    private int fontSize;
    private int fontType;

    private Key() {
      this(null, 0, 0);
    }

    Key(String fontName, int fontSize, int fontType) {
      this.fontName = fontName;
      this.fontSize = fontSize;
      this.fontType = fontType;
    }

    @Override
    protected Key clone() {
      return new Key(fontName, fontSize, fontType);
    }

    @Override
    public int hashCode() {
      int result = fontName != null ? fontName.hashCode() : 0;
      result = 31 * result + fontSize;
      result = 31 * result + fontType;
      return result;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Key key = (Key)o;

      if (fontSize != key.fontSize) return false;
      if (fontType != key.fontType) return false;
      if (fontName != null ? !fontName.equals(key.fontName) : key.fontName != null) return false;

      return true;
    }
  }
}
