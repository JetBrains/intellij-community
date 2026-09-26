// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;

/** @deprecated use {@link StringBuilder} instead */
@ApiStatus.ScheduledForRemoval
@Deprecated
public final class StringBuilderSpinAllocator {
  private StringBuilderSpinAllocator() { }

  /** @deprecated use {@code new StringBuilder()} instead */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static StringBuilder alloc() {
    Logger.getInstance(StringBuilderSpinAllocator.class).warn(new Exception("Use 'new StringBuilder()' instead"));
    return new StringBuilder();
  }

  /** @deprecated just delete the call */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static void dispose(StringBuilder instance) {
    Logger.getInstance(StringBuilderSpinAllocator.class).warn(new Exception("Do not use"));
  }
}
