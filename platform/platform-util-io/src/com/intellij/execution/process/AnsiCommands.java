// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

public final class AnsiCommands {
  public static final int SGR_COMMAND_RESET = 0;
  public static final int SGR_COMMAND_BOLD = 1;
  public static final int SGR_COMMAND_FAINT = 2;
  public static final int SGR_COMMAND_ITALIC = 3;
  public static final int SGR_COMMAND_UNDERLINE = 4;
  public static final int SGR_COMMAND_BLINK_SLOW = 5;
  public static final int SGR_COMMAND_BLINK_RAPID = 6;
  public static final int SGR_COMMAND_INVERSE = 7;
  public static final int SGR_COMMAND_CONCEAL = 8;
  public static final int SGR_COMMAND_CROSS_OUT = 9;
  public static final int SGR_COMMAND_PRIMARY_FONT = 10;
  public static final int SGR_COMMAND_FONT1 = 11;
  public static final int SGR_COMMAND_FONT2 = 12;
  public static final int SGR_COMMAND_FONT3 = 13;
  public static final int SGR_COMMAND_FONT4 = 14;
  public static final int SGR_COMMAND_FONT5 = 15;
  public static final int SGR_COMMAND_FONT6 = 16;
  public static final int SGR_COMMAND_FONT7 = 17;
  public static final int SGR_COMMAND_FONT8 = 18;
  public static final int SGR_COMMAND_FONT9 = 19;
  public static final int SGR_COMMAND_FRAKTUR = 20;
  public static final int SGR_COMMAND_DOUBLE_UNDERLINE = 21;
  public static final int SGR_COMMAND_NO_BOLD_FAINT = 22;
  public static final int SGR_COMMAND_NO_ITALIC_FRAKTUR = 23;
  public static final int SGR_COMMAND_NO_UNDERLINE = 24;
  public static final int SGR_COMMAND_NO_BLINK = 25;
  public static final int SGR_COMMAND_NO_INVERSE = 27;
  public static final int SGR_COMMAND_REVEAL = 28;
  public static final int SGR_COMMAND_NO_CROSS_OUT = 29;
  public static final int SGR_COMMAND_FG_COLOR0 = 30;
  public static final int SGR_COMMAND_FG_COLOR1 = 31;
  public static final int SGR_COMMAND_FG_COLOR2 = 32;
  public static final int SGR_COMMAND_FG_COLOR3 = 33;
  public static final int SGR_COMMAND_FG_COLOR4 = 34;
  public static final int SGR_COMMAND_FG_COLOR5 = 35;
  public static final int SGR_COMMAND_FG_COLOR6 = 36;
  public static final int SGR_COMMAND_FG_COLOR7 = 37;
  public static final int SGR_COMMAND_FG_COLOR_ENCODED = 38;
  public static final int SGR_COMMAND_FG_COLOR_DEFAULT = 39;
  public static final int SGR_COMMAND_BG_COLOR0 = 40;
  public static final int SGR_COMMAND_BG_COLOR1 = 41;
  public static final int SGR_COMMAND_BG_COLOR2 = 42;
  public static final int SGR_COMMAND_BG_COLOR3 = 43;
  public static final int SGR_COMMAND_BG_COLOR4 = 44;
  public static final int SGR_COMMAND_BG_COLOR5 = 45;
  public static final int SGR_COMMAND_BG_COLOR6 = 46;
  public static final int SGR_COMMAND_BG_COLOR7 = 47;
  public static final int SGR_COMMAND_BG_COLOR_ENCODED = 48;
  public static final int SGR_COMMAND_BG_COLOR_DEFAULT = 49;
  public static final int SGR_COMMAND_FRAMED = 51;
  public static final int SGR_COMMAND_ENCIRCLED = 52;
  public static final int SGR_COMMAND_OVERLINED = 53;
  public static final int SGR_COMMAND_NO_FRAMED_ENCIRCLED = 54;
  public static final int SGR_COMMAND_NO_OVERLINED = 55;
  public static final int SGR_COMMAND_IDEOGRAM_UNDER_RIGHT = 60;
  public static final int SGR_COMMAND_IDEOGRAM_UNDER_RIGHT_DOUBLE = 61;
  public static final int SGR_COMMAND_IDEOGRAM_OVER_LEFT = 62;
  public static final int SGR_COMMAND_IDEOGRAM_OVER_LEFT_DOUBLE = 63;
  public static final int SGR_COMMAND_IDEOGRAM_STRESS = 64;
  public static final int SGR_COMMAND_IDEOGRAM_OFF = 65;
  public static final int SGR_COMMAND_FG_COLOR8 = 90;
  public static final int SGR_COMMAND_FG_COLOR9 = 91;
  public static final int SGR_COMMAND_FG_COLOR10 = 92;
  public static final int SGR_COMMAND_FG_COLOR11 = 93;
  public static final int SGR_COMMAND_FG_COLOR12 = 94;
  public static final int SGR_COMMAND_FG_COLOR13 = 95;
  public static final int SGR_COMMAND_FG_COLOR14 = 96;
  public static final int SGR_COMMAND_FG_COLOR15 = 97;
  public static final int SGR_COMMAND_BG_COLOR8 = 100;
  public static final int SGR_COMMAND_BG_COLOR9 = 101;
  public static final int SGR_COMMAND_BG_COLOR10 = 102;
  public static final int SGR_COMMAND_BG_COLOR11 = 103;
  public static final int SGR_COMMAND_BG_COLOR12 = 104;
  public static final int SGR_COMMAND_BG_COLOR13 = 105;
  public static final int SGR_COMMAND_BG_COLOR14 = 106;
  public static final int SGR_COMMAND_BG_COLOR15 = 107;
  // these are used with SGR_COMMAND_BG_COLOR_ENCODED/SGR_COMMAND_FG_COLOR_ENCODED
  public static final int SGR_COLOR_ENCODING_INDEXED = 5;
  public static final int SGR_COLOR_ENCODING_RGB = 2;

  private AnsiCommands() {
  }
}
