// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.idea.devkit.threadingModelHelper.ConstraintType
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

internal data class LockTypeFilterChip(val type: ConstraintType, val selected: Boolean)

@Composable
internal fun FilterToggleChip(chip: LockTypeFilterChip, onToggle: () -> Unit) {
  val bg = if (chip.selected) retrieveColorOrUnspecified("List.selectionBackground") else retrieveColorOrUnspecified("List.background")
  val border = JewelTheme.globalColors.borders.normal
  val textColor = if (chip.selected) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.normal
  Box(
    modifier = Modifier
      .background(bg, RoundedCornerShape(12.dp))
      .border(1.dp, border, RoundedCornerShape(12.dp))
      .clickable(role = Role.Checkbox, interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle() }
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(text = chip.type.name, color = textColor, style = JewelTheme.defaultTextStyle)
  }
}
