// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun PathListItem(
  path: ExecutionPath,
  isSelected: Boolean,
  modifier: Modifier = Modifier,
) {
  val colorKey = if (isSelected) "List.selectionBackground" else "List.selectionInactiveBackground"
  val backgroundColor = retrieveColorOrUnspecified(colorKey)
  val textColor = JewelTheme.globalColors.text.normal

  Column(
    modifier = modifier
      .background(backgroundColor)
      .padding(horizontal = 16.dp, vertical = 10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      path.methodChain.forEachIndexed { index, call ->
        if (index > 0) {
          Icon(
            key = AllIconsKeys.General.ArrowRight,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = JewelTheme.globalColors.text.disabled
          )
        }

        val cls = call.method.containingClass?.name ?: "Unknown"
        val methodName = call.method.name

        Text(
          text = "$cls.$methodName",
          style = JewelTheme.defaultTextStyle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = textColor
        )
      }

      Icon(
        key = AllIconsKeys.General.ArrowRight,
        contentDescription = null,
        modifier = Modifier.size(12.dp),
        tint = JewelTheme.globalColors.text.disabled
      )

      LockRequirementChip(path.lockRequirement, isHighlighted = isSelected)
    }
  }
}