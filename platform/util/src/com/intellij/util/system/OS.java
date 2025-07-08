// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import com.intellij.execution.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum OS {
  Windows, macOS, Linux, FreeBSD, Other;

  /**
   * A string representation of the OS version.
   * The format is system-dependent ("major.minor" for Windows and macOS, kernel version for Linux, etc.)
   */
  public final @NotNull String version = getOsVersion();

  /** Represents an operating system this JVM is running on */
  public static final OS CURRENT = fromString(System.getProperty("os.name"));

  public static @NotNull OS fromString(@Nullable String os) {
    if (os != null) {
      os = os.toLowerCase(Locale.ENGLISH);
      if (os.startsWith("windows")) return Windows;
      if (os.startsWith("mac")) return macOS;
      if (os.startsWith("linux")) return Linux;
      if (os.startsWith("freebsd")) return FreeBSD;
    }
    return Other;
  }

  private static String getOsVersion() {
    String name = System.getProperty("os.name");
    String version = System.getProperty("os.version", "unknown").toLowerCase(Locale.ENGLISH);
    if (name.startsWith("Windows") && name.matches("Windows \\d+")) {
      // for whatever reason, JRE reports "Windows 11" as a name and "10.0" as a version on Windows 11
      try {
        String version2 = name.substring("Windows".length() + 1) + ".0";
        if (Float.parseFloat(version2) > Float.parseFloat(version)) {
          version = version2;
        }
      }
      catch (NumberFormatException ignored) { }
    }
    return version;
  }

  public @NotNull Platform getPlatform() {
    return this == Windows ? Platform.WINDOWS : Platform.UNIX;
  }

  /**
   * Returns {@code true} if the current operating system is a generic Unix-like system (not Windows or macOS).
   */
  public static boolean isGenericUnix() {
    return CURRENT != Windows && CURRENT != macOS;
  }
}
