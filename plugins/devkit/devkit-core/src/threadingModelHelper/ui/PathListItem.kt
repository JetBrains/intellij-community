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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqsAnalyzer
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun PathListItem(
  path: LockReqsAnalyzer.Companion.ExecutionPath,
  isSelected: Boolean,
  isActive: Boolean,
  modifier: Modifier = Modifier,
) {
  val backgroundColor = when {
    isSelected && isActive -> retrieveColorOrUnspecified("List.selectionBackground")
    isSelected && !isActive -> retrieveColorOrUnspecified("List.selectionInactiveBackground")
    else -> Color.Transparent
  }

  Column(
    modifier = modifier
      .background(backgroundColor)
      .padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      path.methodChain.forEachIndexed { index, method ->
        if (index > 0) {
          Icon(
            key = AllIconsKeys.General.ArrowRight,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = JewelTheme.globalColors.text.disabled
          )
        }

        Text(
          text = "${method.containingClass?.name?.substringAfterLast('.')}.${method.name}",
          style = JewelTheme.defaultTextStyle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = if (isSelected && isActive) Color.White else JewelTheme.globalColors.text.normal
        )
      }

      Icon(
        key = AllIconsKeys.General.ArrowRight,
        contentDescription = null,
        modifier = Modifier.size(12.dp),
        tint = JewelTheme.globalColors.text.disabled
      )

      LockRequirementChip(path.lockRequirement, isSelected && isActive)
    }
  }
}
