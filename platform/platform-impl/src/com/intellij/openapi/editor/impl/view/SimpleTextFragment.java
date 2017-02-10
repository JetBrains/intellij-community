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
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.FontInfo;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;

/**
 * Fragment of text for which complex layout is not required. Rendering is the same as if each character would be rendered on its own.
 */
class SimpleTextFragment extends TextFragment {
  @NotNull
  private final char[] myText;
  @NotNull
  private final Font myFont;

  SimpleTextFragment(@NotNull char[] lineChars, int start, int end, @NotNull FontInfo fontInfo) {
    super(end - start);
    myText = Arrays.copyOfRange(lineChars, start, end);
    myFont = fontInfo.getFont();
    float x = 0;
    for (int i = 0; i < myText.length; i++) {
      x += fontInfo.charWidth2D(myText[i]);
      myCharPositions[i] = x;
    }
  }

  @Override
  boolean isRtl() {
    return false;
  }

  @Override
  int offsetToLogicalColumn(int offset) {
    return offset;
  }

  @Override
  public void draw(Graphics2D g, float x, float y, int startColumn, int endColumn) {
    g.setFont(myFont);
    int xAsInt = (int)x;
    int yAsInt = (int)y;
    if (x == xAsInt && y == yAsInt) { // avoid creating garbage if possible
      g.drawChars(myText, startColumn, endColumn - startColumn, xAsInt, yAsInt);
    }
    else {
      g.drawString(new String(myText, startColumn, endColumn - startColumn), x, y);
    }
  }

  @Override
  public int getLogicalColumnCount(int startColumn) {
    return myCharPositions.length;
  }

  @Override
  public int getVisualColumnCount(float startX) {
    return myCharPositions.length;
  }

  @Override
  public int[] xToVisualColumn(float startX, float x) {
    float relX = x - startX;
    float prevPos = 0;
    for (int i = 0; i < myCharPositions.length; i++) {
      float newPos = myCharPositions[i];
      if (relX < (newPos + prevPos) / 2) {
        return new int[] {i, relX <= prevPos ? 0 : 1};
      }
      prevPos = newPos;
    }
    return new int[] {myCharPositions.length, relX <= myCharPositions[myCharPositions.length - 1] ? 0 : 1};
  }

  @Override
  public float visualColumnToX(float startX, int column) {
    return startX + getX(column);
  }
}
