package com.intellij.driver.sdk.ui.components.common.popups

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.UiComponent

/** The Air global prompt (`AgentPrompt.OpenGlobalPalette`). */
fun Finder.agentPromptPalette(): UiComponent =
  x("Air global prompt") { byClass("AgentPromptPalettePanel") }
