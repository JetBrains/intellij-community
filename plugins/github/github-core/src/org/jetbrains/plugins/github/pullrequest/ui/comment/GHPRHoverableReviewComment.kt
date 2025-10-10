// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import kotlinx.coroutines.flow.StateFlow

interface GHPRHoverableReviewComment {
  val shouldShowOutline: StateFlow<Boolean>
  fun showOutline(isHovered: Boolean)
  val isDimmed: StateFlow<Boolean>
  fun setDimmed(isDimmed: Boolean)
  val isFocused: StateFlow<Boolean>
  fun setFocused(isFocused: Boolean)
}