// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;

/**
 * StringBuilderSpinAllocator reuses StringBuilder instances performing non-blocking allocation and dispose.
 * @deprecated Use {@code new} {@link StringBuilder} and don't be smarter than necessary
 */
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
@Deprecated
public final class StringBuilderSpinAllocator {
  private StringBuilderSpinAllocator() {
  }

  /**
   * @deprecated Use {@link StringBuilder}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @Deprecated
  public static StringBuilder alloc() {
    DeprecatedMethodException.report("Use 'new StringBuilder()' instead");
    return new StringBuilder();
  }

  /**
   * @deprecated Use nothing instead, stop worrying, let GC do its job and start living
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @Deprecated
  public static void dispose(StringBuilder instance) {
    DeprecatedMethodException.report("Do not use");
  }
}
