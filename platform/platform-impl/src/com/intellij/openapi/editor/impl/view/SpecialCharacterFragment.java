// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.ui.paint.LinePainter2D;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Consumer;

class SpecialCharacterFragment implements LineFragment {
  private static final Int2ObjectMap<String> SPECIAL_CHAR_CODES = new Int2ObjectOpenHashMap<>(
  // @formatter:off
    new int[] {
        0x00,   0x01,   0x02,   0x03,   0x04,   0x05,   0x06,   0x07,   0x08,                   0x0B,   0x0C,           0x0E,   0x0F,
        0x10,   0x11,   0x12,   0x13,   0x14,   0x15,   0x16,   0x17,   0x18,   0x19,   0x1A,   0x1B,   0x1C,   0x1D,   0x1E,   0x1F,
        0x7F,
                0x81,   0x82,   0x83,   0x84,   0x85,   0x86,   0x87,   0x88,   0x89,   0x8A,   0x8B,   0x8C,   0x8D,   0x8E,   0x8F,
        0x90,   0x91,   0x92,   0x93,   0x94,   0x95,   0x96,   0x97,   0x98,   0x99,   0x9A,   0x9B,   0x9C,   0x9D,   0x9E,   0x9F,
        0xA0,                                                                                                   0xAD,
      0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200A, 0x200B, 0x200C, 0x200D, 0x200E, 0x200F,
                                                                      0x2028, 0x2029, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x202F,
                                                                                                                              0X205F,
      0x2060, 0x2061, 0x2062, 0x2063, 0x2064,         0x2066, 0x2067, 0x2068, 0x2069, 0x206A, 0x206B, 0x206C, 0x206D, 0x206E, 0x206F,
      0xFEFF
    },
    new String[] {
       "NUL",  "SOH",  "STX",  "ETX",  "EOT",  "ENQ",  "ACK",  "BEL",   "BS",                   "VT",   "FF",           "SO",   "SI",
       "DLE",  "DC1",  "DC2",  "DC3",  "DC4",  "NAK",  "SYN",  "ETB",  "CAN",   "EM",  "SUB",  "ESC",   "FS",   "GS",   "RS",   "US",
       "DEL",
               "HOP",  "BPH",  "NBH",  "IND",  "NEL",  "SSA",  "ESA",  "HTS",  "HTJ",  "VTS",  "PLD",  "PLU",   "RI",  "SS2",  "SS3",
       "DCS",  "PU1",  "PU2",  "STS",  "CCH",   "MW",  "SPA",  "EPA",  "SOS", "SGCI",  "SCI",  "CSI",   "ST",  "OSC",   "PM",  "APC",
      "NBSP",                                                                                                  "SHY",
      "NQSP", "MQSP", "ENSP", "EMSP","3/MSP","4/MSP","6/MSP",  "FSP",  "PSP", "THSP",  "HSP", "ZWSP", "ZWNJ",  "ZWJ",  "LRM",  "RLM",
                                                                      "LSEP", "PSEP",  "LRE",  "RLE",  "PDF",  "LRO",  "RLO","NNBSP",
                                                                                                                              "MMSP",
        "WJ",  "f()",   "x",    ",",    "+",           "LRI",  "RLI",  "FSI",  "PDI",  "ISS",  "ASS", "IAFS", "AAFS", "NADS", "NODS",
    "ZWNBSP"
    }
  // @formatter:on
  );

  private static final int BRACKETS_DISTANCE_TO_TEXT = 4;
  private static final int BRACKETS_SIZE = 2;
  private static final int BRACKETS_THICKNESS = 1;

  static @Nullable SpecialCharacterFragment create(@NotNull EditorView view, int c,  char @Nullable [] text, int pos) {
    String code = SPECIAL_CHAR_CODES.get(c);
    if (code == null) return null;
    if (text != null) {
      // special cases when we shouldn't render special characters explicitly, as they can impact the rendering of surrounding characters
      if (c == 0xA0 || c >= 0x2000 && c <= 0x200A || c == 0x202F || c == 0x205F) { // 'special' space characters (having non-zero width)
        if (pos < text.length - 1 && Character.getType(text[pos + 1]) == Character.NON_SPACING_MARK) {
          // space characters (NBSP in particular) can be used to display combining marks in isolation
          return null;
        }
      }
      else if (c == 0x200C /*ZWNJ*/ || c == 0x200D /*ZWJ*/) {
        // we don't display ZWNJ/ZWJ surrounded by non-ASCII characters, to avoid breaking complex scripts and emoji display
        if (pos > 0 && text[pos - 1] >= 128) {
          return null;
        }
        if (pos < text.length - 1 && text[pos + 1] >= 128) {
          return null;
        }
      }
    }
    return new SpecialCharacterFragment(view, code);
  }

  private final EditorView myView;
  private final String myCode;
  private final float myWidth;

  SpecialCharacterFragment(@NotNull EditorView view, @NotNull String code) {
    myView = view;
    myCode = code;
    FontMetrics fontMetrics = FontInfo.getFontMetrics(getFont(), view.getFontRenderContext());
    myWidth = FontLayoutService.getInstance().stringWidth(fontMetrics, code);
  }

  @Override
  public int getLength() {
    return 1;
  }

  @Override
  public int getLogicalColumnCount(int startColumn) {
    return 1;
  }

  @Override
  public int getVisualColumnCount(float startX) {
    return 1;
  }

  @Override
  public int logicalToVisualColumn(float startX, int startColumn, int column) {
    return column;
  }

  @Override
  public int visualToLogicalColumn(float startX, int startColumn, int column) {
    return column;
  }

  @Override
  public int visualColumnToOffset(float startX, int column) {
    return column;
  }

  @Override
  public float visualColumnToX(float startX, int column) {
    return startX + (column <= 0 ? 0 : myWidth);
  }

  @Override
  public int[] xToVisualColumn(float startX, float x) {
    if (x <= startX) return new int[] {0, 0};
    if (x > startX + myWidth) return new int[] {1, 1};
    int column = (x <= startX + myWidth / 2) ? 0 : 1;
    return new int[] {column, 1 - column};
  }

  @Override
  public float offsetToX(float startX, int startOffset, int offset) {
    return startX + (startOffset >= offset ? 0 : myWidth);
  }

  @Override
  public Consumer<Graphics2D> draw(float x, float y, int startOffset, int endOffset) {
    return g -> {
      g.setFont(getFont());
      g.drawString(myCode, x, y);

      // draw brackets around the character code
      float xEnd = x + myWidth - 1;
      float yTop = y - Math.min(myView.getAscent(), myView.getCapHeight() + BRACKETS_DISTANCE_TO_TEXT);
      float yBottom = y + Math.min(myView.getDescent(), BRACKETS_DISTANCE_TO_TEXT) - 1;
      LinePainter2D.paint(g, x, yTop, xEnd, yTop, LinePainter2D.StrokeType.INSIDE, BRACKETS_THICKNESS);
      LinePainter2D.paint(g, x, yTop + BRACKETS_SIZE, x, yTop, LinePainter2D.StrokeType.INSIDE, BRACKETS_THICKNESS);
      LinePainter2D.paint(g, xEnd, yTop + BRACKETS_SIZE, xEnd, yTop, LinePainter2D.StrokeType.INSIDE, BRACKETS_THICKNESS);
      LinePainter2D.paint(g, x, yBottom, xEnd, yBottom, LinePainter2D.StrokeType.INSIDE, BRACKETS_THICKNESS);
      LinePainter2D.paint(g, x, yBottom - BRACKETS_SIZE, x, yBottom, LinePainter2D.StrokeType.INSIDE, BRACKETS_THICKNESS);
      LinePainter2D.paint(g, xEnd, yBottom - BRACKETS_SIZE, xEnd, yBottom, LinePainter2D.StrokeType.INSIDE, BRACKETS_THICKNESS);
    };
  }

  @Override
  public @NotNull LineFragment subFragment(int startOffset, int endOffset) {
    return this;
  }

  private Font getFont() {
    return myView.getEditor().getColorsScheme().getFont(EditorFontType.PLAIN);
  }
}
