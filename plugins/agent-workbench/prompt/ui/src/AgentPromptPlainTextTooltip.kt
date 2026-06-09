// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Point
import javax.swing.JComponent

internal fun buildPlainTextTooltipComponent(text: @NlsSafe String): JBTextArea {
  return JBTextArea(text).apply {
    isEditable = false
    isFocusable = false
    lineWrap = false
    wrapStyleWord = false
    isOpaque = true
    background = UIUtil.getToolTipBackground()
    foreground = UIUtil.getToolTipForeground()
    font = UIUtil.getToolTipFont()
    border = JBUI.Borders.empty(4, 6)
  }
}

internal fun createPlainTextIdeTooltip(component: JComponent, textProvider: () -> @NlsSafe String): IdeTooltip {
  return object : IdeTooltip(component, Point(0, 0), null, component) {
    init {
      layer = Balloon.Layer.top
      preferredPosition = Balloon.Position.above
    }

    override fun beforeShow(): Boolean {
      val text = textProvider().takeIf { it.isNotBlank() } ?: return false
      tipComponent = buildPlainTextTooltipComponent(text)
      return true
    }
  }
}

internal fun createContextChipIdeTooltip(component: JComponent, entryProvider: () -> ContextEntry): IdeTooltip {
  return object : IdeTooltip(component, Point(0, 0), null, component) {
    init {
      layer = Balloon.Layer.top
      preferredPosition = Balloon.Position.above
    }

    override fun beforeShow(): Boolean {
      val entry = entryProvider()
      val tooltipText = entry.tooltipText.takeIf { it.isNotBlank() } ?: return false
      tipComponent = AgentPromptScreenshotChipIcon.buildPreviewTooltipComponent(
        item = entry.item,
        labelText = entry.displayText,
      ) ?: buildPlainTextTooltipComponent(tooltipText)
      return true
    }
  }
}

internal fun installPlainTextIdeTooltip(component: JComponent, textProvider: () -> @NlsSafe String) {
  IdeTooltipManager.getInstance().setCustomTooltip(component, createPlainTextIdeTooltip(component, textProvider))
}

internal fun installContextChipIdeTooltip(component: JComponent, entryProvider: () -> ContextEntry) {
  IdeTooltipManager.getInstance().setCustomTooltip(component, createContextChipIdeTooltip(component, entryProvider))
}
