// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

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
import androidx.compose.ui.unit.dp
import org.jetbrains.idea.devkit.threadingModelHelper.ConstraintType
import org.jetbrains.idea.devkit.threadingModelHelper.LockRequirement
import org.jetbrains.idea.devkit.threadingModelHelper.RequirementReason
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun LockRequirementChip(
  requirement: LockRequirement,
  isHighlighted: Boolean,
  modifier: Modifier = Modifier
) {
  val (icon, color, text) = when (requirement.requirementReason) {
    RequirementReason.ANNOTATION -> Triple(
      AllIconsKeys.Nodes.Annotationtype,
      JewelTheme.globalColors.text.info,
      when (requirement.constraintType) {
        ConstraintType.READ -> "@RequiresReadLock"
        ConstraintType.NO_READ -> "@RequiresNoReadAccess"
        ConstraintType.WRITE -> "@RequiresWriteLock"
        ConstraintType.WRITE_INTENT -> "@RequiresWriteIntentLock"
        ConstraintType.EDT -> "@RequiresEdt"
        ConstraintType.BGT -> "@RequiresBackgroundThread"
      }
    )
    RequirementReason.ASSERTION -> Triple(
      AllIconsKeys.Debugger.ThreadAtBreakpoint,
      JewelTheme.globalColors.text.normal,
      when (requirement.constraintType) {
        ConstraintType.READ -> "assertReadAccess()"
        ConstraintType.NO_READ -> "assertNoReadAccess()"
        ConstraintType.WRITE -> "assertWriteAccess()"
        ConstraintType.WRITE_INTENT -> "assertWriteIntentAllowed()"
        ConstraintType.EDT -> "assertIsEdt()"
        ConstraintType.BGT -> "assertIsBackgroundThread()"
      }
    )
    RequirementReason.SWING_COMPONENT -> Triple(
      AllIconsKeys.Nodes.Artifact,
      JewelTheme.globalColors.text.normal,
      "Swing UI access"
    )
    RequirementReason.MESSAGE_BUS -> Triple(
      AllIconsKeys.General.BalloonInformation,
      JewelTheme.globalColors.text.normal,
      "MessageBus listener"
    )
    RequirementReason.IMPLICIT -> Triple(
      AllIconsKeys.General.Information,
      JewelTheme.globalColors.text.disabled,
      when (requirement.constraintType) {
        ConstraintType.READ -> "Implicit read lock"
        ConstraintType.NO_READ -> "Implicit no-read"
        ConstraintType.WRITE -> "Implicit write lock"
        ConstraintType.WRITE_INTENT -> "Implicit write-intent"
        ConstraintType.EDT -> "Implicit EDT"
        ConstraintType.BGT -> "Implicit background"
      }
    )
  }

  val bg = if (isHighlighted) color.copy(alpha = 0.25f) else color.copy(alpha = 0.10f)
  val border = if (isHighlighted) color.copy(alpha = 0.6f) else color.copy(alpha = 0.3f)

  Row(
    modifier = modifier
      .background(bg, RoundedCornerShape(4.dp))
      .border(1.dp, border, RoundedCornerShape(4.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(key = icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
    Text(text = text, color = color)
  }
}