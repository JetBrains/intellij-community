// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.wsl;

public final class WslConstants {
  /**
   * Historical prefix; no longer the default since WSL 1.0 (Windows 11 and store-installed WSL on Windows 10).
   * Do not rely on it for constructing WSL paths, take the prefix from a project path instead.
   *
   * @see com.intellij.execution.wsl.WslPath
   */
  public static final String UNC_PREFIX = "\\\\wsl$\\";

  @SuppressWarnings("SpellCheckingInspection")
  public static final String WSLENV = "WSLENV";
}
