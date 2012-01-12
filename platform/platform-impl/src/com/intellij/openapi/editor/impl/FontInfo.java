/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.editor.markup.TextAttributes;
import gnu.trove.TIntHashSet;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class FontInfo {
  private final String myFamilyName;
  private final Font myFont;
  private final int mySize;
  @TextAttributes.FontStyle private final int myStyle;
  private final TIntHashSet mySafeCharacters = new TIntHashSet();
  private FontMetrics myFontMetrics = null;
  private final int[] charWidth = new int[128];

  public FontInfo(final String familyName, final int size, @TextAttributes.FontStyle int style) {
    myFamilyName = familyName;
    mySize = size;
    myStyle = style;
    myFont = new Font(familyName, style, size);
  }

  public boolean canDisplay(char c) {
    try {
      if (c < 128) return true;
      if (mySafeCharacters.contains(c)) return true;
      if (myFont.canDisplay(c)) {
        mySafeCharacters.add(c);
        return true;
      }
      return false;
    }
    catch (Exception e) {
      // JRE has problems working with the font. Just skip.
      return false;
    }
  }

  public Font getFont() {
    return myFont;
  }

  public int charWidth(char c, JComponent anyComponent) {
    final FontMetrics metrics = fontMetrics(anyComponent);
    if (c < 128) return charWidth[c];
    return metrics.charWidth(c);
  }

  private FontMetrics fontMetrics(JComponent anyComponent) {
    if (myFontMetrics == null) {
      myFontMetrics = anyComponent.getFontMetrics(myFont);
      for (int i = 0; i < 128; i++) {
        charWidth[i] = myFontMetrics.charWidth(i);
      }
    }
    return myFontMetrics;
  }

  public int getSize() {
    return mySize;
  }

  @TextAttributes.FontStyle
  public int getStyle() {
    return myStyle;
  }
}
