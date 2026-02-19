// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Introduce your own installation identifiers as needed. Third-party plugins are not allowed to use JetBrains installation ID.
 */
@Deprecated(forRemoval = true)
public final class PermanentInstallationID {
  /**
   * No-op, always returns "0".
   *
   * @deprecated Introduce your own installation identifiers as needed. Third-party plugins are not allowed to use JetBrains installation ID.
   */
  @Deprecated(forRemoval = true)
  public static @NotNull String get() {
    return "0";
  }
}
