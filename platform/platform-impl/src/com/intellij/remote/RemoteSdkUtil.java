// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class RemoteSdkUtil {
  /**
   * From <a href="http://pubs.opengroup.org/onlinepubs/000095399/basedefs/xbd_chap08.html">The Open Group</a>:
   * <p>
   * <cite>
   * These strings have the form name=value; names shall not contain the
   * character '='. For values to be portable across systems conforming to IEEE
   * Std 1003.1-2001, the value shall be composed of characters from the
   * portable character set (except NUL and as indicated below).
   * </cite>
   *
   * @param string a string to be checked
   * @return {@code true} if provided {@code string} is a invalid environment name and {@code false} otherwise
   */
  public static boolean isInvalidLinuxEnvName(@Nullable String string) {
    return StringUtil.isEmptyOrSpaces(string) || string.contains("=");
  }

  public static void remoteIncorrectVariables(Map<String, String> envs) {
    envs.entrySet().removeIf(e -> isInvalidLinuxEnvName(e.getKey()));
  }
}
