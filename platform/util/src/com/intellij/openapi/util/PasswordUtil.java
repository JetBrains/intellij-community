// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Credentials MUST BE stored in ({@link com.intellij.ide.passwordSafe.PasswordSafe})
 */
@Deprecated
public final class PasswordUtil {
  private PasswordUtil() { }

  // weak encryption just to avoid plain text passwords in text files
  public static String encodePassword(@Nullable String password) {
    if (password == null) {
      return "";
    }

    StringBuilder result = new StringBuilder();
    for (int i = 0; i < password.length(); i++) {
      result.append(Integer.toHexString(password.charAt(i) ^ 0xdfaa));
    }
    return result.toString();
  }

  @NotNull
  public static String encodePassword(char @Nullable [] password) {
    if (password == null) {
      return "";
    }

    StringBuilder result = new StringBuilder();
    for (char c : password) {
      result.append(Integer.toHexString(c ^ 0xdfaa));
    }
    return result.toString();
  }

  public static String decodePassword(@Nullable String password) throws NumberFormatException {
    return password == null ? "" : new String(decodePasswordAsCharArray(password));
  }

  public static char @NotNull [] decodePasswordAsCharArray(@Nullable String password) throws NumberFormatException {
    if (StringUtil.isEmpty(password)) {
      return ArrayUtilRt.EMPTY_CHAR_ARRAY;
    }

    char[] result = new char[password.length() / 4];
    for (int i = 0, j = 0; i < password.length(); i += 4, j++) {
      int c = Integer.parseInt(password.substring(i, i + 4), 16);
      c ^= 0xdfaa;
      result[j] = new Character((char)c).charValue();
    }
    return result;
  }
}