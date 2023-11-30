// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import com.intellij.execution.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum OS {
  Windows, macOS, Linux, FreeBSD, Other;

  /** Represents an operating system this JVM is running on */
  public static final OS CURRENT = fromString(System.getProperty("os.name"));

  @NotNull
  public Platform getPlatform() {
    return this == Windows ? Platform.WINDOWS : Platform.UNIX;
  }

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
}
