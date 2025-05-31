// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DiffConfig {
  const val USE_PATIENCE_ALG: Boolean = false
  const val USE_GREEDY_MERGE_MAGIC_RESOLVE: Boolean = false
  const val DELTA_THRESHOLD_SIZE: Int = 20000
  const val MAX_BAD_LINES: Int = 3 // Do not try to compare lines by-word after that many prior failures.
  const val UNIMPORTANT_LINE_CHAR_COUNT: Int = 3
}
