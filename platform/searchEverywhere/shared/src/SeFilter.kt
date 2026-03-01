// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SeFilter {
  fun toState(): SeFilterState

  /**
   * Compares the current filter with another filter to determine equality.
   *
   * @param other The filter to be compared with the current filter.
   * @return `true` if the filters are considered equal, `false` otherwise.
   */
  fun isEqualTo(other: SeFilter): Boolean
}