// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqsAnalyzer
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys


@Composable
internal fun LockRequirementChip(
  requirement: LockReqsAnalyzer.Companion.LockRequirement,
  isHighlighted: Boolean,
) {
  val (icon, color, text) = when (requirement.type) {
    LockReqsAnalyzer.Companion.LockCheckType.ANNOTATION -> Triple(
      AllIconsKeys.Nodes.Annotationtype,
      JewelTheme.globalColors.text.info,
      "@RequiresReadLock"
    )
    LockReqsAnalyzer.Companion.LockCheckType.ASSERTION -> Triple(
      AllIconsKeys.Debugger.ThreadAtBreakpoint,
      JewelTheme.globalColors.text.normal,
      "assertReadAccess()"
    )
  }

  val backgroundColor = if (isHighlighted) color.copy(alpha = 0.3f) else color.copy(alpha = 0.1f)
  val borderColor = if (isHighlighted) color.copy(alpha = 0.6f) else color.copy(alpha = 0.3f)

  Row(
    modifier = Modifier
      .background(backgroundColor, RoundedCornerShape(4.dp))
      .border(1.dp, borderColor, RoundedCornerShape(4.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      key = icon,
      contentDescription = null,
      modifier = Modifier.size(14.dp),
      tint = color
    )
    Text(
      text = text,
      fontWeight = FontWeight.Medium,
      color = color
    )
  }
}
