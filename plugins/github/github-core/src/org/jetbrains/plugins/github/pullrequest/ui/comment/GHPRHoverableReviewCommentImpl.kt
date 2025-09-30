// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GHPRHoverableReviewCommentImpl : GHPRHoverableReviewComment {
  private val _isHovered: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val shouldShowOutline: StateFlow<Boolean> = _isHovered.asStateFlow()

  override fun showOutline(isHovered: Boolean) {
    _isHovered.value = isHovered
  }

  private val _isDimmed: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val isDimmed: StateFlow<Boolean> = _isDimmed.asStateFlow()

  override fun setDimmed(isDimmed: Boolean) {
    _isDimmed.value = isDimmed
  }
}