// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;

/**
 * A stripped-down version of {@link com.intellij.openapi.util.SystemInfo}.
 * Intended for use by external (out-of-IDE-process) runners and helpers, so it should not contain any library dependencies.
 */
public final class SystemInfoRt {
  public static final String OS_NAME;
  public static final String OS_VERSION;

  static {
    String name = System.getProperty("os.name");
    String version = System.getProperty("os.version").toLowerCase(Locale.ENGLISH);

    if (name.startsWith("Windows") && name.matches("Windows \\d+")) {
      // for whatever reason, JRE reports "Windows 11" as a name and "10.0" as a version on Windows 11
      try {
        String version2 = name.substring("Windows".length() + 1) + ".0";
        if (Float.parseFloat(version2) > Float.parseFloat(version)) {
          version = version2;
        }
      }
      catch (NumberFormatException ignored) { }
      name = "Windows";
    }

    OS_NAME = name;
    OS_VERSION = version;
  }

  private static final String _OS_NAME = OS_NAME.toLowerCase(Locale.ENGLISH);
  public static final boolean isWindows = _OS_NAME.startsWith("windows");
  public static final boolean isMac = _OS_NAME.startsWith("mac");
  public static final boolean isLinux = _OS_NAME.startsWith("linux");
  public static final boolean isFreeBSD = _OS_NAME.startsWith("freebsd");
  /** @deprecated press 'F' */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isSolaris = false;
  public static final boolean isUnix = !isWindows;
  /** @deprecated confusing name; consider using {@code com.intellij.util.system.OS.isGenericUnix()} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isXWindow = isUnix && !isMac;

  public static final boolean isFileSystemCaseSensitive =
    isUnix && !isMac || "true".equalsIgnoreCase(System.getProperty("idea.case.sensitive.fs"));

  private SystemInfoRt() {}
}
