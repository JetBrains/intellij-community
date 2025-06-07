// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import com.intellij.diff.util.MergeConflictResolutionStrategy.*

/**
 * Represents the strategy which will be used to resolve merge conflicts.
 * @property DEFAULT - Only available when there is no conflict
 * @property TEXT - Use the fact that changes do not overlap at the word level, see [com.intellij.diff.comparison.ComparisonMergeUtil]
 * @property SEMANTIC - Use structure of the file to resolve conflict, see [com.intellij.diff.merge.LangSpecificMergeConflictResolver]
 */
enum class MergeConflictResolutionStrategy {
  DEFAULT,
  TEXT,
  SEMANTIC
}