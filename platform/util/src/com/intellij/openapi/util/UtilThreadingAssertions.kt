// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object UtilThreadingAssertions {
  @Volatile
  private var BACKGROUND_ACTIVITY_ASSERT: Runnable? = null

  /**
   * Soft-asserts that the caller is performing heavy background activity from an appropriate context,
   * namely on a background thread and without holding the read lock.
   *
   * Intended to be called at the entry of long-running or blocking operations (e.g. spawning external
   * processes) so that misuse is caught early.
   */
  @JvmStatic
  fun softAssertHeavyBackgroundActivity() {
    BACKGROUND_ACTIVITY_ASSERT?.run()
  }

  @JvmStatic
  fun init(backgroundActivityAssert: Runnable? = null) {
    BACKGROUND_ACTIVITY_ASSERT = backgroundActivityAssert
  }
}