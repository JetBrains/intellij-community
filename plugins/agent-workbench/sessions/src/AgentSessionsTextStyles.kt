package com.intellij.agent.workbench.sessions

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.typography

internal object AgentSessionsTextStyles {
  // Use theme typography so sizes track IntelliJ Platform defaults.
  @Composable
  fun projectTitle(): TextStyle {
    return JewelTheme.typography.h4TextStyle
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
