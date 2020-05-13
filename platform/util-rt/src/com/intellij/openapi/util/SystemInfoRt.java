// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.ReviseWhenPortedToJDK;

import java.util.Locale;

/**
 * A stripped-down version of {@link com.intellij.openapi.util.SystemInfo}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 */
public final class SystemInfoRt {
  public static final String OS_NAME = System.getProperty("os.name");
  public static final String OS_VERSION = System.getProperty("os.version").toLowerCase(Locale.ENGLISH);

  private static final String _OS_NAME = OS_NAME.toLowerCase(Locale.ENGLISH);
  public static final boolean isWindows = _OS_NAME.startsWith("windows");
  public static final boolean isMac = _OS_NAME.startsWith("mac");
  public static final boolean isLinux = _OS_NAME.startsWith("linux");
  public static final boolean isFreeBSD = _OS_NAME.startsWith("freebsd");
  public static final boolean isSolaris = _OS_NAME.startsWith("sunos");
  public static final boolean isUnix = !isWindows;

  public static final boolean isFileSystemCaseSensitive =
    isUnix && !isMac || "true".equalsIgnoreCase(System.getProperty("idea.case.sensitive.fs"));

  private static final String ARCH_DATA_MODEL = System.getProperty("sun.arch.data.model");
  public static final boolean is32Bit = ARCH_DATA_MODEL == null || ARCH_DATA_MODEL.equals("32");
  public static final boolean is64Bit = !is32Bit;

  @ReviseWhenPortedToJDK("9")
  public static final boolean IS_AT_LEAST_JAVA9 = isModularJava();

  @SuppressWarnings("JavaReflectionMemberAccess")
  private static boolean isModularJava() {
    try {
      Class.class.getMethod("getModule");
      return true;
    }
    catch (Throwable t) {
      return false;
    }
  }
}