// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.util.ui.JBUI.CurrentTheme
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

internal open class LinuxCustomFrameButtons(myCloseAction: Action) : CustomFrameButtons {
  companion object {
    fun create(closeAction: Action): CustomFrameButtons {
      val darculaTitleButtons = LinuxCustomFrameButtons(closeAction)
      darculaTitleButtons.createChildren()
      return darculaTitleButtons
    }
  }

  private val panel = TitleButtonsPanel()

  protected val closeButton: LinuxFrameButton = LinuxFrameButton(myCloseAction, LinuxFrameButton.Type.CLOSE)

  override var isCompactMode: Boolean
    set(value) {
      panel.isCompactMode = value
    }
    get() {
      return panel.isCompactMode
    }

  protected open fun createChildren() {
    addComponent(closeButton)
    updateVisibility()
  }

  override fun getContent(): JComponent = panel

  override fun updateVisibility() {
  }

  override fun onUpdateFrameActive() {
    closeButton.updateStyle()
  }

  protected fun addComponent(component: JComponent) {
    panel.addComponent(component)
  }
}

private class TitleButtonsPanel : JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) {
  var isCompactMode = false
    set(value) {
      field = value
      updateScaledPreferredSize()
    }

  init {
    isOpaque = false
  }

  fun addComponent(component: JComponent) {
    component.setScaledPreferredSize()
    add(component, "top")
  }

  private fun updateScaledPreferredSize() {
    components.forEach { (it as? JComponent)?.setScaledPreferredSize() }
    revalidate()
    repaint()
  }

  private fun JComponent.setScaledPreferredSize() {
    val size = CurrentTheme.TitlePane.buttonPreferredSize(UISettings.defFontScale).clone() as Dimension
    size.height = CustomWindowHeaderUtil.getPreferredWindowHeaderHeight(isCompactMode)
    preferredSize = Dimension(size.width, size.height)
  }

  override fun updateUI() {
    super.updateUI()
    components?.forEach { component ->
      if (component is JComponent) {
        component.setScaledPreferredSize()
      }
    }
  }
}