// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common

import com.intellij.ui.JBColor
import java.awt.Color

data class AgentThreadActivityPresentation(
  val namedColorKey: String,
  val lightFallbackRgb: Int,
  val darkFallbackRgb: Int,
  val statusMessageKey: String,
) {
  val namedColor: JBColor = JBColor.namedColor(namedColorKey, JBColor(lightFallbackRgb, darkFallbackRgb))
}

private fun threadActivityPresentation(
  namedColorKey: String,
  lightFallbackColor: Int,
  darkFallbackColor: Int,
  statusMessageKey: String,
): AgentThreadActivityPresentation {
  return AgentThreadActivityPresentation(
    namedColorKey = namedColorKey,
    lightFallbackRgb = lightFallbackColor,
    darkFallbackRgb = darkFallbackColor,
    statusMessageKey = statusMessageKey,
  )
}

private val READY_PRESENTATION = threadActivityPresentation(
  namedColorKey = "AgentWorkbench.ThreadStatus.ready",
  lightFallbackColor = 0x3FE47E,
  darkFallbackColor = 0x57965C,
  statusMessageKey = "toolwindow.thread.status.ready",
)

private val PROCESSING_PRESENTATION = threadActivityPresentation(
  namedColorKey = "AgentWorkbench.ThreadStatus.processing",
  lightFallbackColor = 0xFF9F43,
  darkFallbackColor = 0xE08855,
  statusMessageKey = "toolwindow.thread.status.in.progress",
)

private val REVIEWING_PRESENTATION = threadActivityPresentation(
  namedColorKey = "AgentWorkbench.ThreadStatus.reviewing",
  lightFallbackColor = 0x2FD1C4,
  darkFallbackColor = 0x20B2AA,
  statusMessageKey = "toolwindow.thread.status.needs.review",
)

private val UNREAD_PRESENTATION = threadActivityPresentation(
  namedColorKey = "AgentWorkbench.ThreadStatus.unread",
  lightFallbackColor = 0x4DA3FF,
  darkFallbackColor = 0x548AF7,
  statusMessageKey = "toolwindow.thread.status.needs.input",
)

fun AgentThreadActivity.presentation(): AgentThreadActivityPresentation {
  return when (this) {
    AgentThreadActivity.READY -> READY_PRESENTATION
    AgentThreadActivity.PROCESSING -> PROCESSING_PRESENTATION
    AgentThreadActivity.REVIEWING -> REVIEWING_PRESENTATION
    AgentThreadActivity.UNREAD -> UNREAD_PRESENTATION
  }
}

fun AgentThreadActivity.statusColor(): Color = presentation().namedColor

fun AgentThreadActivity.statusMessageKey(): String = presentation().statusMessageKey
