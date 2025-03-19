// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.CustomFrameButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.LinuxCustomFrameButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.hideNativeLinuxTitle
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.swing.JLabel
import javax.swing.UIManager

internal class DialogHeader(window: Window) : CustomHeader(window) {
  private val titleLabel = JLabel().apply {
    border = LABEL_BORDER
  }
  private val titleChangeListener = PropertyChangeListener {
    titleLabel.text = getTitle()
  }

  init {
    layout = GridBagLayout()
    titleLabel.text = getTitle()

    productIcon.border = JBUI.Borders.empty(0, H, 0, H)

    val gb = GridBag().setDefaultFill(GridBagConstraints.VERTICAL).setDefaultAnchor(GridBagConstraints.WEST)
    add(productIcon, gb.next())
    add(titleLabel, gb.next().fillCell().weightx(1.0))
    createButtonsPane()?.let { add(it.getContent(), gb.next().anchor(GridBagConstraints.EAST)) }
  }

  private val dragListener = object : MouseAdapter() { //passing events to OS handler to make it draggable
    override fun mouseDragged(e: MouseEvent?) {
      customTitleBar?.forceHitTest(false)
    }

    override fun mouseClicked(e: MouseEvent?) {
      customTitleBar?.forceHitTest(false)
    }

    override fun mouseMoved(e: MouseEvent?) {
      customTitleBar?.forceHitTest(false)
    }
  }

  override fun installListeners() {
    super.installListeners()
    window.addPropertyChangeListener("title", titleChangeListener)
    addMouseListener(dragListener)
    addMouseMotionListener(dragListener)
  }

  override fun uninstallListeners() {
    super.uninstallListeners()
    window.removePropertyChangeListener(titleChangeListener)
    removeMouseListener(dragListener)
    removeMouseMotionListener(dragListener)
  }

  override fun updateActive() {
    titleLabel.foreground = if (isActive) UIManager.getColor("Panel.foreground") else UIManager.getColor("Label.disabledForeground")
    super.updateActive()
  }

  override fun windowStateChanged() {
    super.windowStateChanged()
    titleLabel.text = getTitle()
  }

  override fun addNotify() {
    super.addNotify()
    titleLabel.text = getTitle()
  }

  @NlsContexts.DialogTitle
  private fun getTitle(): String? {
    when (window) {
      is Dialog -> return window.title
      else -> return ""
    }
  }

  override fun getComponentGraphics(g: Graphics?): Graphics {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g))
  }

  private fun createButtonsPane(): CustomFrameButtons? {
    return if (hideNativeLinuxTitle(UISettings.shadowInstance)) LinuxCustomFrameButtons.create(createCloseAction(this)) else null
  }
}