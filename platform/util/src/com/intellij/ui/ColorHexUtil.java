// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public final class ColorHexUtil {
  @NotNull
  public static Color fromHex(@NotNull String str) {
    Color color = fromHexOrNull(str);
    if (color != null) return color;
    throw new IllegalArgumentException("unsupported length:" + str);
  }

  /**
   * Return Color object from string. The following formats are allowed:
   * {@code 0xA1B2C3},
   * {@code #abc123},
   * {@code ABC123},
   * {@code ab5},
   * {@code #FFF}.
   *
   * @param str hex string
   * @return Color object
   */
  @SuppressWarnings("UseJBColor")
  @Nullable
  public static Color fromHexOrNull(@Nullable String str) {
    if (str == null) return null;
    int pos = str.startsWith("#") ? 1 : str.startsWith("0x") ? 2 : 0;
    int len = str.length() - pos;
    if (len == 3) return new Color(fromHex1(str, pos), fromHex1(str, pos + 1), fromHex1(str, pos + 2), 255);
    if (len == 4) return new Color(fromHex1(str, pos), fromHex1(str, pos + 1), fromHex1(str, pos + 2), fromHex1(str, pos + 3));
    if (len == 6) return new Color(fromHex2(str, pos), fromHex2(str, pos + 2), fromHex2(str, pos + 4), 255);
    if (len == 8) return new Color(fromHex2(str, pos), fromHex2(str, pos + 2), fromHex2(str, pos + 4), fromHex2(str, pos + 6));
    return null;
  }

  @Nullable
  public static Color fromHex(@Nullable String str, @Nullable Color defaultValue) {
    if (str == null) return defaultValue;
    try {
      return fromHex(str);
    }
    catch (Exception e) {
      return defaultValue;
    }
  }

  private static int fromHex(@NotNull String str, int pos) {
    char ch = str.charAt(pos);
    if (ch >= '0' && ch <= '9') return ch - '0';
    if (ch >= 'A' && ch <= 'F') return ch - 'A' + 10;
    if (ch >= 'a' && ch <= 'f') return ch - 'a' + 10;
    throw new IllegalArgumentException("unsupported char at " + pos + ":" + str);
  }

  private static int fromHex1(@NotNull String str, int pos) {
    return 17 * fromHex(str, pos);
  }

  private static int fromHex2(@NotNull String str, int pos) {
    return 16 * fromHex(str, pos) + fromHex(str, pos + 1);
  }
}
