// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** @deprecated Credentials MUST BE stored in ({@link com.intellij.ide.passwordSafe.PasswordSafe}). */
@Deprecated
@ApiStatus.ScheduledForRemoval
@SuppressWarnings("unused")
public final class PasswordUtil {
  private PasswordUtil() { }

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

  public static @NotNull String encodePassword(char @Nullable [] password) {
    return password == null || password.length == 0 ? "" : encodePassword(new String(password));
  }

  public static String decodePassword(@Nullable String password) throws NumberFormatException {
    return password == null || password.isEmpty() ? "" : new String(decodePasswordAsCharArray(password));
  }

  public static char @NotNull [] decodePasswordAsCharArray(@Nullable String password) throws NumberFormatException {
    if (password == null || password.isEmpty()) {
      return new char[]{};
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
