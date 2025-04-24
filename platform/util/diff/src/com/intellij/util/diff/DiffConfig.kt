// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff

import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
object DiffConfig {
  private const val DIFF_DELTA_THRESHOLD_SIZE_KEY: @NonNls String = "idea.diff.delta.threshold.size"
  private const val DIFF_DELTA_PATIENCE_ALG_KEY: @NonNls String = "idea.diff.force.patience.alg"

  @JvmField
  val USE_PATIENCE_ALG: Boolean = SystemProperties.getBooleanProperty(DIFF_DELTA_PATIENCE_ALG_KEY, false)
  const val USE_GREEDY_MERGE_MAGIC_RESOLVE: Boolean = false
  @JvmField
  val DELTA_THRESHOLD_SIZE: Int = SystemProperties.getIntProperty(DIFF_DELTA_THRESHOLD_SIZE_KEY, 20000)
  const val MAX_BAD_LINES: Int = 3 // Do not try to compare lines by-word after that many prior failures.
  const val UNIMPORTANT_LINE_CHAR_COUNT: Int = 3
}
