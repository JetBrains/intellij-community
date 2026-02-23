package com.intellij.agent.workbench.sessions

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.simpleListItemStyle
import org.jetbrains.jewel.ui.theme.treeStyle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

@Composable
internal fun threadIndicatorColor(thread: AgentSessionThread): Color {
  return Color(thread.activity.argb)
}

@Composable
internal fun subAgentIndicatorColor(): Color {
  // TODO: map sub-agent status to indicator color once status is available.
  return JewelTheme.globalColors.text.disabled
}

@Composable
internal fun treeRowBackground(
  isHovered: Boolean,
  isSelected: Boolean,
  isActive: Boolean,
  baseTint: Color = Color.Unspecified,
): Color {
  if (isSelected || isActive) return Color.Transparent
  val hoverBase = rowHoverBase()
  if (isHovered && hoverBase.isSpecified) {
    return hoverBase.copy(alpha = 0.12f)
  }
  return if (baseTint.isSpecified) baseTint.copy(alpha = 0.06f) else Color.Transparent
}

@Composable
internal fun treeRowShape(): RoundedCornerShape {
  val cornerSize = JewelTheme.treeStyle.metrics.simpleListItemMetrics.selectionBackgroundCornerSize
  return RoundedCornerShape(cornerSize)
}

@Composable
internal fun treeRowSpacing(): Dp {
  return JewelTheme.treeStyle.metrics.simpleListItemMetrics.iconTextGap
}

@Composable
internal fun projectRowTint(): Color {
  return projectRowTintBase()
}

@Composable
internal fun threadIndicatorSize(): Dp {
  return indicatorSize(scale = 0.22f, min = 5.dp, max = 9.dp)
}

@Composable
internal fun subAgentIndicatorSize(): Dp {
  return indicatorSize(scale = 0.14f, min = 3.dp, max = 6.dp)
}

@Composable
internal fun loadingIndicatorSize(): Dp {
  return indicatorSize(scale = 0.28f, min = 10.dp, max = 14.dp)
}

@Composable
internal fun projectActionIconSize(): Dp {
  return indicatorSize(scale = 0.28f, min = 14.dp, max = 18.dp)
}

@Composable
internal fun projectActionSlotSize(): Dp {
  val iconSize = projectActionIconSize()
  val loadingSize = loadingIndicatorSize()
  return if (iconSize > loadingSize) iconSize else loadingSize
}

@Composable
private fun indicatorSize(scale: Float, min: Dp, max: Dp): Dp {
  val baseSize = JewelTheme.treeStyle.metrics.elementMinHeight * scale
  return baseSize.coerceIn(min, max)
}

@Composable
private fun projectRowTintBase(): Color {
  val treeColors = JewelTheme.treeStyle.colors
  val listColors = JewelTheme.simpleListItemStyle.colors
  return treeColors.backgroundSelected
    .takeOrElse { treeColors.backgroundSelectedActive }
    .takeOrElse { listColors.backgroundSelected }
    .takeOrElse { listColors.backgroundSelectedActive }
}

@Composable
private fun rowHoverBase(): Color {
  val treeColors = JewelTheme.treeStyle.colors
  val listColors = JewelTheme.simpleListItemStyle.colors
  return listColors.backgroundActive
    .takeOrElse { treeColors.backgroundSelectedActive }
    .takeOrElse { treeColors.backgroundSelected }
}

internal fun formatRelativeTimeShort(timestamp: Long, now: Long): String {
  val absSeconds = abs(((timestamp - now) / 1000.0).roundToLong())
  if (absSeconds < 60) {
    return AgentSessionsBundle.message("toolwindow.time.now")
  }
  if (absSeconds < 60 * 60) {
    val value = max(1, (absSeconds / 60.0).roundToLong())
    return "${value}m"
  }
  if (absSeconds < 60 * 60 * 24) {
    val value = max(1, (absSeconds / (60.0 * 60.0)).roundToLong())
    return "${value}h"
  }
  if (absSeconds < 60 * 60 * 24 * 7) {
    val value = max(1, (absSeconds / (60.0 * 60.0 * 24.0)).roundToLong())
    return "${value}d"
  }
  if (absSeconds < 60 * 60 * 24 * 30) {
    val value = max(1, (absSeconds / (60.0 * 60.0 * 24.0 * 7.0)).roundToLong())
    return "${value}w"
  }
  if (absSeconds < 60 * 60 * 24 * 365) {
    val value = max(1, (absSeconds / (60.0 * 60.0 * 24.0 * 30.0)).roundToLong())
    return "${value}mo"
  }
  val value = max(1, (absSeconds / (60.0 * 60.0 * 24.0 * 365.0)).roundToLong())
  return "${value}y"
}
