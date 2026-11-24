// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.model.IconInspectorController
import com.intellij.devkit.icons.inspector.model.RendererStability
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text

internal fun amberColor(dark: Boolean) = if (dark) Color(0xFFE5A94D) else Color(0xFFB36B00)
private fun greenColor(dark: Boolean) = if (dark) Color(0xFF63C07A) else Color(0xFF2B8A4B)

internal fun formatDuration(ms: Long): String {
  if (ms < 1000) return "$ms ms"
  val totalSeconds = ms / 1000.0
  if (totalSeconds < 60) return String.format("%.1f s", totalSeconds)
  val minutes = (ms / 60000).toInt()
  val seconds = ((ms % 60000) / 1000).toInt()
  return if (minutes < 60) "$minutes min $seconds s" else "${minutes / 60} h ${minutes % 60} min"
}

@Composable
internal fun StabilityBanner(stability: RendererStability?, controller: IconInspectorController) {
  // null = static/legacy tree, nothing to measure; paints == 0 = captured target whose first paint hasn't arrived yet
  if (stability == null || stability.paints == 0) {
    val key = if (stability == null) "ui.stability.unknown" else "ui.stability.measuring"
    Text(
      DevkitIconsInspectorBundle.message(key),
      color = JewelTheme.globalColors.text.info,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    )
    return
  }
  val dark = JewelTheme.isDark
  val churning = stability.recreations > 0
  val color = if (churning) amberColor(dark) else greenColor(dark)
  val observed = formatDuration(stability.observedMs)
  val message = when {
    !churning -> DevkitIconsInspectorBundle.message("ui.stability.stable", observed, stability.paints)
    stability.paints > 0 && stability.recreations * 2 >= stability.paints ->
      DevkitIconsInspectorBundle.message(
        "ui.stability.churning.always", stability.recreations, observed, stability.distinctInstances,
        formatDuration(stability.lastRecreationAgoMs ?: 0),
      )
    stability.lastRecreationAgoMs == null ->
      DevkitIconsInspectorBundle.message("ui.stability.churning.never", stability.recreations, observed, stability.distinctInstances)
    else ->
      DevkitIconsInspectorBundle.message(
        "ui.stability.churning", stability.recreations, observed, stability.distinctInstances,
        formatDuration(stability.lastRecreationAgoMs),
      )
  }
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
      .background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
      .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
      .padding(horizontal = 8.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(message, color = color, modifier = Modifier.weight(1f))
    Link(DevkitIconsInspectorBundle.message("ui.stability.reset"), onClick = controller::resetStability)
  }
}
