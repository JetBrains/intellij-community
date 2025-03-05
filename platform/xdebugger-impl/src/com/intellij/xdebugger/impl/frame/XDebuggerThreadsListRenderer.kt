// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UpdateScaleHelper
import com.intellij.xdebugger.frame.XExecutionStack.AdditionalDisplayInfo
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseEvent
import javax.swing.*

internal class XDebuggerThreadsListRenderer : ColoredListCellRenderer<StackInfo>() {

  override fun customizeCellRenderer(
    list: JList<out StackInfo>,
    value: StackInfo?,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean,
  ) {
    val stack = value ?: return
    if (selected) {
      background = UIUtil.getListSelectionBackground(hasFocus)
      foreground = NamedColorUtil.getListSelectionForeground(hasFocus)
      mySelectionForeground = foreground
    }
    renderText(stack)

    SpeedSearchUtil.applySpeedSearchHighlighting(list, this, true, selected)
  }

  override fun getToolTipText(event: MouseEvent): String? {
    return renderTooltip(event) ?: super.getToolTipText(event)
  }
}

internal class XDebuggerThreadsListRendererWithDescription : JPanel(VerticalLayout(4)), ListCellRenderer<StackInfo> {

  private val threadNameLabel = SimpleColoredComponent()
  private val threadDescriptionLabel = SimpleColoredComponent()
  private val updateScaleHelper = UpdateScaleHelper()

  init {
    add(threadNameLabel)
    add(threadDescriptionLabel)
    border = JBUI.Borders.empty(4)
  }

  override fun getPreferredSize(): Dimension {
    val minSize = super.getPreferredSize()
    return Dimension(0, minSize.height)
  }

  override fun getListCellRendererComponent(list: JList<out StackInfo?>?, value: StackInfo?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component? {
    threadNameLabel.clear()
    threadDescriptionLabel.clear()
    value ?: return this

    this.background = null
    threadNameLabel.background = null
    threadDescriptionLabel.background = null

    val backgroundValue: Color?
    val foregroundValue: Color?

    if (!isSelected) {
      backgroundValue = null
      foregroundValue = null
    }
    else {
      backgroundValue = UIUtil.getListSelectionBackground(cellHasFocus)
      foregroundValue = NamedColorUtil.getListSelectionForeground(cellHasFocus)
    }

    this.background = backgroundValue
    threadNameLabel.background = backgroundValue
    threadDescriptionLabel.background = backgroundValue
    this.foreground = foregroundValue
    threadNameLabel.foreground = foregroundValue
    threadDescriptionLabel.foreground = foregroundValue

    updateScaleHelper.saveScaleAndUpdateUIIfChanged(this)
    threadNameLabel.renderText(value)

    threadDescriptionLabel.append(value.description, SimpleTextAttributes.GRAYED_ATTRIBUTES, JBUI.scale(16), SwingConstants.LEFT)
    return this
  }

  override fun getToolTipText(event: MouseEvent): String? {
    return threadNameLabel.renderTooltip(event) ?: return super.getToolTipText(event)
  }
}

private fun SimpleColoredComponent.renderText(stack: StackInfo) {
  this.append(stack.displayText)
  stack.additionalDisplayText?.let {
    append("   ")
    append(it.text, SimpleTextAttributes.GRAYED_ATTRIBUTES, it)
  }
  icon = stack.icon
}

private fun SimpleColoredComponent.renderTooltip(event: MouseEvent): String? {
  val fragmentIndex = findFragmentAt(event.x)
  if (fragmentIndex == -1) return null

  val additionalDisplayInfo = getFragmentTag(fragmentIndex) as AdditionalDisplayInfo?
  return additionalDisplayInfo?.tooltip
}