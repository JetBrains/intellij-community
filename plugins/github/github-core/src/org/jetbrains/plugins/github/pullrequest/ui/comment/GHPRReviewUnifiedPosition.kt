// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.util.RefComparisonChange
import org.jetbrains.annotations.ApiStatus

/**
 * This class indicates the position of an object inside a diff as a single ordered number.
 * In practice, the line number is always the right-side mapping of the location.
 * If a left-side location has been removed, the right-side mapping is estimated to be the first line above the changed section.
 *
 * Line numbers are supposed to be 0-indexed.
 */
@ApiStatus.Internal
data class GHPRReviewUnifiedPosition(
  val change: RefComparisonChange,
  val leftLine: Int,
  val rightLine: Int,
)