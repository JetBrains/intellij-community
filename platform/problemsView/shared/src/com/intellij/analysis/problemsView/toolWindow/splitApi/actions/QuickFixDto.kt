// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow.splitApi.actions

import com.intellij.ide.ui.icons.IconId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
enum class PriorityDto {
  TOP, HIGH, NORMAL, LOW, BOTTOM
}

@ApiStatus.Internal
@Serializable
data class QuickFixDto(
  val text: String,
  val familyName: String,
  val intentionId: String,
  val options: List<QuickFixDto> = emptyList(),
  val displayName: String? = null,
  val iconId: IconId? = null,
  val hasOptions: Boolean = false,
  val isSelectable: Boolean = false,
  val priority: PriorityDto? = null
)