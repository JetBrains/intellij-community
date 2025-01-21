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
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class XDebuggerThreadsListRenderer(private val withDescription: Boolean) : ColoredListCellRenderer<StackInfo>() {

  private val threadDescriptionComponent = ThreadDescriptionComponent()

  override fun getListCellRendererComponent(list: JList<out StackInfo>, value: StackInfo, index: Int, selected: Boolean, hasFocus: Boolean): Component {
    if (withDescription)
      return threadDescriptionComponent.customizeComponent(value, selected, hasFocus)
    else
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus)
  }

  override fun customizeCellRenderer(
    list: JList<out StackInfo>,
    value: StackInfo?,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean
  ) {
    val stack = value ?: return
    if (selected) {
      background = UIUtil.getListSelectionBackground(hasFocus)
      foreground = NamedColorUtil.getListSelectionForeground(hasFocus)
      mySelectionForeground = foreground
    }
    append(stack.displayText)
    stack.additionalDisplayText?.let {
      append("   ")
      append(it.text, SimpleTextAttributes.GRAYED_ATTRIBUTES, it)
    }
    icon = stack.icon

    SpeedSearchUtil.applySpeedSearchHighlighting(list, this, true, selected)
  }

  override fun getToolTipText(event: MouseEvent): String? {
    val fragmentIndex = findFragmentAt(event.x)
    if (fragmentIndex == -1) return super.getToolTipText(event)

    val additionalDisplayInfo = getFragmentTag(fragmentIndex) as AdditionalDisplayInfo?
    return additionalDisplayInfo?.tooltip ?: super.getToolTipText(event)
  }
}

internal class ThreadDescriptionComponent : JPanel(VerticalLayout(4)) {

  private val threadNameLabel = SimpleColoredComponent()
  private val threadDescriptionLabel = SimpleColoredComponent()
  private val updateScaleHelper = UpdateScaleHelper()

  init {
    add(threadNameLabel)
    add(threadDescriptionLabel)
    border = JBUI.Borders.empty(4)
  }

  fun customizeComponent(value: StackInfo, selected: Boolean, hasFocus: Boolean): JComponent {
    threadNameLabel.clear()
    threadDescriptionLabel.clear()

    this.background = null
    threadNameLabel.background = null
    threadDescriptionLabel.background = null

    val backgroundValue: Color?
    val foregroundValue: Color?

    if (!selected) {
      backgroundValue = null
      foregroundValue = null
    }
    else {
      backgroundValue = UIUtil.getListSelectionBackground(hasFocus)
      foregroundValue = NamedColorUtil.getListSelectionForeground(hasFocus)
    }

    this.background = backgroundValue
    threadNameLabel.background = backgroundValue
    threadDescriptionLabel.background = backgroundValue
    this.foreground = foregroundValue
    threadNameLabel.foreground = foregroundValue
    threadDescriptionLabel.foreground = foregroundValue

    updateScaleHelper.saveScaleAndUpdateUIIfChanged(this)
    threadNameLabel.append(value.displayText)
    value.additionalDisplayText?.let {
      threadNameLabel.append("   ")
      threadNameLabel.append(it.text, SimpleTextAttributes.GRAYED_ATTRIBUTES, it)
    }
    threadNameLabel.icon = value.icon

    threadDescriptionLabel.append(value.description, SimpleTextAttributes.GRAYED_ATTRIBUTES, JBUI.scale(16), SwingConstants.LEFT)

    //TODO [chernyaev] tooltip for thread panel
    //this.toolTipText = item.additionalDisplayText?.tooltip

    return this
  }

  override fun getPreferredSize(): Dimension {
    val minSize =  super.getPreferredSize()
    return Dimension(0, minSize.height)
  }
}