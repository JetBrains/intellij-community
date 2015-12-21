/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
      x += fontInfo.charWidth(myText[i]);
      myCharPositions[i] = x;
    }
  }

  @Override
  boolean isRtl() {
    return false;
  }

  @Override
  public void draw(Graphics2D g, float x, float y, int startColumn, int endColumn) {
    g.setFont(myFont);
    g.drawChars(myText, startColumn, endColumn - startColumn, (int)x, (int)y);
  }  
}
