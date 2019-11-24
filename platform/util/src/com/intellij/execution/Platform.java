// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

public enum Platform {
  WINDOWS('\\', ';', "\r\n"), UNIX('/', ':', "\n");

  public final char fileSeparator;
  public final char pathSeparator;
  public final String lineSeparator;

  Platform(char fileSeparator, char pathSeparator, String lineSeparator) {
    this.fileSeparator = fileSeparator;
    this.pathSeparator = pathSeparator;
    this.lineSeparator = lineSeparator;
  }

  @NotNull
  public static Platform current() {
    return SystemInfo.isWindows ? WINDOWS : UNIX;
  }
}
