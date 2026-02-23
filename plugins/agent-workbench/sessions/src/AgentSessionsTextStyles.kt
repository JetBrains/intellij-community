package com.intellij.agent.workbench.sessions

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.typography

internal object AgentSessionsTextStyles {
  // Use theme typography so sizes track IntelliJ Platform defaults.
  @Composable
  fun projectTitle(isOpen: Boolean = false): TextStyle {
    val baseStyle = JewelTheme.typography.h4TextStyle
    return if (isOpen) baseStyle.copy(fontWeight = FontWeight.SemiBold) else baseStyle.copy(fontWeight = FontWeight.Normal)
  }

  @Composable
  fun threadTitle(): TextStyle {
    return JewelTheme.typography.regular
  }

  @Composable
  fun threadTime(): TextStyle {
    return JewelTheme.typography.small
  }

  @Composable
  fun subAgentTitle(): TextStyle {
    return JewelTheme.typography.small
  }

  @Composable
  fun emptyState(): TextStyle {
    return JewelTheme.typography.small
  }

  @Composable
  fun error(): TextStyle {
    return JewelTheme.typography.small
  }
}
