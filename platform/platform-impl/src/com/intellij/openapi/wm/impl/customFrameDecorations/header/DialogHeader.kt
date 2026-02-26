// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.toggleMaximized
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.CustomFrameButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.LinuxCustomFrameButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.WindowsDialogButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.hideNativeLinuxTitle
import com.intellij.util.system.OS
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import java.awt.Dialog
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.swing.JDialog
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

    override fun mouseClicked(e: MouseEvent) {
      // On macOS, the OS handles maximizing on header double-click.
      // On Linux, we don't receive mouse events for the header, so we can't do anything easily about it.
      // But on Windows, we receive these events thanks to using a custom header, but the OS doesn't have native maximization.
      // So let's handle it manually.
      if (OS.CURRENT == OS.Windows && window is JDialog && e.id == MouseEvent.MOUSE_CLICKED && e.clickCount == 2) {
        window.toggleMaximized()
        e.consume()
      }
      else {
        customTitleBar?.forceHitTest(false)
      }
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
    return when (OS.CURRENT) {
      OS.Windows -> if (ApplicationManager.getApplication() != null) WindowsDialogButtons() else null
      OS.Linux -> if (hideNativeLinuxTitle(UISettings.shadowInstance)) LinuxCustomFrameButtons.create(createCloseAction(this)) else null
      else -> null
    }
  }
}