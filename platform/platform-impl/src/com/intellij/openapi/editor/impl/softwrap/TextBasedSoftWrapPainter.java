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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.impl.ColorHolder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.impl.TextDrawingCallback;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * {@link SoftWrapPainter} implementation that uses target unicode symbols as soft wrap drawings.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jul 1, 2010 5:30:43 PM
 */
public class TextBasedSoftWrapPainter implements SoftWrapPainter {

  private final Map<SoftWrapDrawingType, char[]> mySymbols = new EnumMap<SoftWrapDrawingType, char[]>(SoftWrapDrawingType.class);
  private final Map<SoftWrapDrawingType, FontInfo> myFonts = new EnumMap<SoftWrapDrawingType, FontInfo>(SoftWrapDrawingType.class);
  private final Map<SoftWrapDrawingType, Integer> myWidths = new EnumMap<SoftWrapDrawingType, Integer>(SoftWrapDrawingType.class);
  private final Map<SoftWrapDrawingType, Integer> myVGaps = new EnumMap<SoftWrapDrawingType, Integer>(SoftWrapDrawingType.class);

  private final TextDrawingCallback myDrawingCallback;
  private final ColorHolder myColorHolder;
  private final boolean myCanUse;

  public TextBasedSoftWrapPainter(Map<SoftWrapDrawingType, Character> symbols, Editor editor, TextDrawingCallback drawingCallback,
                                  ColorHolder colorHolder)
    throws IllegalArgumentException
  {
    if (symbols.size() != SoftWrapDrawingType.values().length) {
      throw new IllegalArgumentException(
        String.format("Can't create text-based soft wrap painter. Reason: given 'drawing type -> symbol' mappings "
                      + "are incomplete - expected size %d but got %d (%s)", SoftWrapDrawingType.values().length, symbols.size(), symbols)
      );
    }
    myDrawingCallback = drawingCallback;
    myColorHolder = colorHolder;
    myCanUse = init(symbols, editor);
  }

  @Override
  public int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
    char[] buffer = mySymbols.get(drawingType);
    FontInfo fontInfo = myFonts.get(drawingType);
    int vGap = myVGaps.get(drawingType);
    myDrawingCallback.drawChars(g, buffer, 0, buffer.length, x, y + lineHeight - vGap, myColorHolder.getColor(), fontInfo);
    return getMinDrawingWidth(drawingType);
  }

  @Override
  public int getDrawingHorizontalOffset(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
    return getMinDrawingWidth(drawingType);
  }

  @Override
  public int getMinDrawingWidth(@NotNull SoftWrapDrawingType drawingType) {
    return myWidths.get(drawingType);
  }

  @Override
  public boolean canUse() {
    return myCanUse;
  }

  /**
   * Tries to find fonts that are capable to display all unicode symbols used by the current painter.
   *
   * @param symbols   target symbols to use for drawing
   * @param editor    editor to use during font lookup
   * @return    <code>true</code> if target font that is capable to display all unicode symbols used by the current painter is found;
   *            <code>false</code> otherwise
   */
  private boolean init(Map<SoftWrapDrawingType, Character> symbols, Editor editor) {
    // We use dummy component here in order to being able to work with font metrics.
    JLabel component = new JLabel();

    for (Map.Entry<SoftWrapDrawingType, Character> entry : symbols.entrySet()) {
      FontInfo fontInfo = EditorUtil.fontForChar(entry.getValue(), Font.PLAIN, editor);
      if (!fontInfo.canDisplay(entry.getValue())) {
        return false;
      }
      char[] buffer = new char[1];
      buffer[0] = entry.getValue();
      mySymbols.put(entry.getKey(), buffer);
      myFonts.put(entry.getKey(), fontInfo);
      FontMetrics metrics = component.getFontMetrics(fontInfo.getFont());
      myWidths.put(entry.getKey(), metrics.charWidth(buffer[0]));
      int vGap = metrics.getDescent();
      myVGaps.put(entry.getKey(), vGap);
    }
    return true;
  }
}
