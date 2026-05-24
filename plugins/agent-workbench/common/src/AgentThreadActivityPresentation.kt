// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseJBColor")

package com.intellij.agent.workbench.common

import com.intellij.ui.JBColor
import java.awt.Color

data class AgentThreadActivityPresentation(
  @JvmField val namedColorKey: String?,
  @JvmField val lightFallbackRgb: Int?,
  @JvmField val darkFallbackRgb: Int?,
  @JvmField val statusMessageKey: String,
  @JvmField val showBadge: Boolean = true,
) {
  val namedColor: JBColor? = namedColorKey?.let { key ->
    JBColor.namedColor(key, JBColor(requireNotNull(lightFallbackRgb), requireNotNull(darkFallbackRgb)))
  }

  override fun toString(): String {
    return "AgentThreadActivityPresentation(" +
           "$namedColorKey, " +
           "${lightFallbackRgb?.let { Color(it) }}, " +
           "${darkFallbackRgb?.let { Color(it) }}, " +
           "$statusMessageKey, " +
           "$showBadge" +
           ")"
  }
}

private fun threadActivityPresentation(
  namedColorKey: String,
  lightFallbackColor: Int,
  darkFallbackColor: Int,
  statusMessageKey: String,
  showBadge: Boolean = true,
): AgentThreadActivityPresentation {
  return AgentThreadActivityPresentation(
    namedColorKey = namedColorKey,
    lightFallbackRgb = lightFallbackColor,
    darkFallbackRgb = darkFallbackColor,
    statusMessageKey = statusMessageKey,
    showBadge = showBadge,
  )
}

// Idle/no-signal state: intentionally unaccented and unbadged.
private val READY_PRESENTATION = AgentThreadActivityPresentation(
  namedColorKey = null,
  lightFallbackRgb = null,
  darkFallbackRgb = null,
  statusMessageKey = "toolwindow.thread.status.ready",
  showBadge = false,
)

// Active work in progress.
private val PROCESSING_PRESENTATION = threadActivityPresentation(
  namedColorKey = "AgentWorkbench.ThreadStatus.processing",
  lightFallbackColor = Color(0xFFAF0F).rgb,
  darkFallbackColor = Color(0xF2C55C).rgb,
  statusMessageKey = "toolwindow.thread.status.in.progress",
)

// Review-mode output waiting for user attention.
private val REVIEWING_PRESENTATION = threadActivityPresentation(
  namedColorKey = "AgentWorkbench.ThreadStatus.reviewing",
  lightFallbackColor = Color(0x8F5AE5).rgb,
  darkFallbackColor = Color(0x8F5AE5).rgb,
  statusMessageKey = "toolwindow.thread.status.needs.review",
)

// Completed assistant output waiting to be seen.
private val UNREAD_PRESENTATION = threadActivityPresentation(
  namedColorKey = "AgentWorkbench.ThreadStatus.unread",
  lightFallbackColor = Color(0x55A76A).rgb,
  darkFallbackColor = Color(0x5FAD65).rgb,
  statusMessageKey = "toolwindow.thread.status.done",
)

// Explicit user action required.
private val NEEDS_INPUT_PRESENTATION = threadActivityPresentation(
  namedColorKey = "AgentWorkbench.ThreadStatus.needsInput",
  lightFallbackColor = Color(0x588CF3).rgb,
  darkFallbackColor = Color(0x548AF7).rgb,
  statusMessageKey = "toolwindow.thread.status.needs.input",
)

fun AgentThreadActivity.presentation(): AgentThreadActivityPresentation {
  return when (this) {
    AgentThreadActivity.READY -> READY_PRESENTATION
    AgentThreadActivity.PROCESSING -> PROCESSING_PRESENTATION
    AgentThreadActivity.REVIEWING -> REVIEWING_PRESENTATION
    AgentThreadActivity.NEEDS_INPUT -> NEEDS_INPUT_PRESENTATION
    AgentThreadActivity.UNREAD -> UNREAD_PRESENTATION
  }
}

fun AgentThreadActivity.statusColor(): Color? = presentation().namedColor

fun AgentThreadActivity.statusBadgeColor(): Color? {
  val presentation = presentation()
  return presentation.namedColor?.takeIf { presentation.showBadge }
}

fun AgentThreadActivity.statusMessageKey(): String = presentation().statusMessageKey
