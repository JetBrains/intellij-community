// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.ui.codereview.editor.CodeReviewInlayWithOutlineModel
import com.intellij.diff.util.Side
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GHPRCodeReviewInlayWithOutlineModelImpl : CodeReviewInlayWithOutlineModel {
  override val range: StateFlow<Pair<Side, IntRange>?> = MutableStateFlow(null)
  private val _isDimmed: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val isDimmed: StateFlow<Boolean> = _isDimmed.asStateFlow()

  override fun setDimmed(isDimmed: Boolean) {
    _isDimmed.value = isDimmed
  }

  private val _isFocused: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val isFocused: StateFlow<Boolean> = _isFocused.asStateFlow()
  override fun setFocused(isFocused: Boolean) {
    _isFocused.value = isFocused
  }

  private val _isHovered: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val shouldShowOutline: StateFlow<Boolean> = _isHovered.combineState(isFocused) { isHovered, isFocused -> isFocused || isHovered }

  override fun showOutline(isHovered: Boolean) {
    _isHovered.value = isHovered
  }
}
