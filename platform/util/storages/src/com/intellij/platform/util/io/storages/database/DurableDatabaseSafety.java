// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database;

import org.jetbrains.annotations.ApiStatus;

import static com.intellij.util.SystemProperties.getBooleanProperty;

/// Defines optional safeguards for durable database operations.
///
/// System properties control the balance between safety and performance.
@ApiStatus.Internal
public final class DurableDatabaseSafety {
  //TODO RC: Enable these checks by default in tests

  /// Enables extra consistency checks during normal database operations.
  /// These checks add overhead. They are useful in tests but less useful in production.
  private static final boolean ENABLE_SELF_CHECKS = getBooleanProperty(
    "com.intellij.platform.util.io.storages.database.ENABLE_SELF_CHECKS", false
  );

  public static boolean selfChecksEnabled() {
    return ENABLE_SELF_CHECKS;
  }

  private DurableDatabaseSafety() { }
}
