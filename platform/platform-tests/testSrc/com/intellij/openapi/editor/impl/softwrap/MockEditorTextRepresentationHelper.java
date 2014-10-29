/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;

/**
* @author Denis Zhdanov
* @since Aug 5, 2010 9:18:15 AM
*/
public class MockEditorTextRepresentationHelper implements EditorTextRepresentationHelper {

  private final CharSequence myText;
  private final int mySpaceSizeInPixels;
  private final int myTabSizeInColumns;

  public MockEditorTextRepresentationHelper(CharSequence text, int spaceSizeInPixels, int tabSizeInColumns) {
    myText = text;
    mySpaceSizeInPixels = spaceSizeInPixels;
    myTabSizeInColumns = tabSizeInColumns;
  }

  public int toVisualColumnSymbolsNumber(char c, int x) {
    return new MockEditorTextRepresentationHelper(new String(new char[] {c}), mySpaceSizeInPixels, myTabSizeInColumns)
      .toVisualColumnSymbolsNumber(0, 1, x);
  }

  @Override
  public int toVisualColumnSymbolsNumber(int start, int end, int x) {
    int result = 0;
    for (int i = start; i < end; i++) {
      char c = myText.charAt(i);
      if (c == '\n') {
        result = 0;
        x = 0;
        continue;
      }
      int width = charWidth(c, x);
      result += width / mySpaceSizeInPixels;
      if (width % mySpaceSizeInPixels > 0) {
        result++;
      }
      x += width;
    }
    return result;
  }

  @Override
  public int textWidth(int start, int end, int fontType, int x) {
    int result = 0;
    for (int i = start; i < end; i++) {
      char c = myText.charAt(i);
      switch (c) {
        case '\n': result = 0; break;
        default: result += charWidth(c, result);
      }
    }
    return result;
  }

  public int charWidth(char c, int x) {
    if (c == '\t') {
      int tabWidth = mySpaceSizeInPixels * myTabSizeInColumns;
      int tabsNumber = x / tabWidth;
      return (tabsNumber + 1) * tabWidth - x;
    }
    else {
      return mySpaceSizeInPixels;
    }
  }

  @Override
  public int calcSoftWrapUnawareOffset(int startOffset, int endOffset, int startColumn, int targetColumn, int startX) {
    int x = startX;
    int column = startColumn;
    for (int i = startOffset; i < endOffset; i++) {
      if (column == targetColumn) {
        return i;
      }
      char c = myText.charAt(i);
      int width = charWidth(c, x);
      column += width / mySpaceSizeInPixels;
      if (width % mySpaceSizeInPixels > 0) {
        column++;
      }
      x += width;
    }
    return -1;
  }
}
