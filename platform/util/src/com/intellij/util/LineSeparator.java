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

  private static final Logger LOG = Logger.getInstance(LineSeparator.class);
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
    LOG.error("Invalid string for line separator: " + StringUtil.escapeStringCharacters(string));
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
    if (SystemInfo.isWindows) {
      return CRLF;
    }
    return LF;
  }
}
