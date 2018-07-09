/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>Identifies a line separator:
 * either Unix ({@code \n}), Windows (@{code \r\n}) or (possible not actual anymore) Classic Mac ({@code \r}).</p>
 * <p/>
 * <p>The intention is to use this class everywhere, where a line separator is needed, instead of just Strings.</p>
 *
 * @author Kirill Likhodedov
 */
public enum LineSeparator {
  LF("\n"),
  CRLF("\r\n"),
  CR("\r");

  private final String mySeparatorString;
  private final byte[] myBytes;

  LineSeparator(@NotNull String separatorString) {
    mySeparatorString = separatorString;
    myBytes = separatorString.getBytes(CharsetToolkit.UTF8_CHARSET);
  }

  @NotNull
  public static LineSeparator fromString(@NotNull String string) {
    for (LineSeparator separator : values()) {
      if (separator.getSeparatorString().equals(string)) {
        return separator;
      }
    }
    Logger.getInstance(LineSeparator.class).error("Invalid string for line separator: " + StringUtil.escapeStringCharacters(string));
    return getSystemLineSeparator();
  }

  @NotNull
  public String getSeparatorString() {
    return mySeparatorString;
  }

  @NotNull
  public byte[] getSeparatorBytes() {
    return myBytes;
  }

  public static boolean knownAndDifferent(@Nullable LineSeparator separator1, @Nullable LineSeparator separator2) {
    return separator1 != null && separator2 != null && !separator1.equals(separator2);
  }

  @NotNull
  public static LineSeparator getSystemLineSeparator() {
    return SystemInfo.isWindows ? CRLF : LF;
  }
}