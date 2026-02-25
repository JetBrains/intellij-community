// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons

import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.openapi.wm.impl.customFrameDecorations.header.DialogHeader
import com.intellij.ui.ComponentUtil
import com.intellij.ui.JBColor
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.awaitCancellation
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeEvent
import javax.swing.JComponent
import javax.swing.JPanel

internal class WindowsDialogButtons : CustomFrameButtons {
  /**
   * Has no effect because it's not applicable for dialogs.
   */
  override var isCompactMode: Boolean = false

  private val buttonsPanel: JComponent = createButtonsPanel()

  override fun getContent(): JComponent = buttonsPanel

  override fun updateVisibility() { }

  override fun onUpdateFrameActive() { }
}

private fun createButtonsPanel(): JPanel {
  val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
  buttonsPanel.background = null // we want it to blend with the header, regardless of its color (e.g., when the dialog is not focused)
  val maximizeButton = createMaximizeButton() ?: return buttonsPanel
  buttonsPanel.add(maximizeButton)
  buttonsPanel.launchOnShow("WindowsDialogButtons init and updates") {
    maximizeButton.updateIconInsets() // factor in the header's own border
    buttonsPanel.parent?.revalidate() // for some reason, it's displayed over the system close button otherwise
    val dialog = ComponentUtil.getWindow(buttonsPanel) ?: return@launchOnShow
    fun update() {
      maximizeButton.isActive = dialog.isActive
      maximizeButton.update()
    }
    // Set up manual action updates on size/location change.
    val componentListener = object : ComponentAdapter() {
      override fun componentMoved(e: ComponentEvent?) {
        update()
      }

      override fun componentResized(e: ComponentEvent?) {
        update()
      }
    }
    val windowListener = object : WindowAdapter() {
      override fun windowActivated(e: WindowEvent?) {
        update()
      }

      override fun windowDeactivated(e: WindowEvent?) {
        update()
      }
    }
    try {
      dialog.addComponentListener(componentListener)
      dialog.addWindowListener(windowListener)
      update() // The first update on show, to ensure the correct maximize/normalize state.
      awaitCancellation()
    }
    finally {
      dialog.removeWindowListener(windowListener)
      dialog.removeComponentListener(componentListener)
    }
  }
  return buttonsPanel
}

private fun createMaximizeButton(): WindowsDialogHeaderButton? {
  if (ApplicationManager.getApplication() == null) return null
  val maximizeAction = ActionManager.getInstance().getAction("MaximizeActiveDialog")
  if (maximizeAction == null) return null
  val maximizeButton = WindowsDialogHeaderButton(maximizeAction) {
    val width = JBUI.CurrentTheme.TitlePane.dialogButtonPreferredWidth()
    val height = CustomWindowHeaderUtil.getPreferredWindowHeaderHeightUnscaled(isCompactHeader = false)
    Dimension(width, height) // ActionButton does the scaling
  }
  // Match the overall Windows L&F.
  maximizeButton.setLook(object : IdeaActionButtonLook() {
    override fun getButtonArc(): JBValue = JBValue.Float.EMPTY
    override fun getStateBackground(component: JComponent?, state: Int): Color? {
      return if (state == ActionButtonComponent.NORMAL) null else super.getStateBackground(component, state)
    }
  })
  maximizeButton.updateIconInsets()
  return maximizeButton
}

private class WindowsDialogHeaderButton(
  maximizeAction: AnAction,
  minSize: () -> Dimension,
) : ActionButton(maximizeAction, null, "DialogHeader", minSize) {
  var isActive = true

  override fun presentationPropertyChanged(e: PropertyChangeEvent) {
    super.presentationPropertyChanged(e)
    if (e.propertyName == Presentation.PROP_VISIBLE) {
      isVisible = myPresentation.isVisible
    }
  }

  override fun updateIcon() {
    super.updateIcon()
    val icon = icon
    if (icon != null) {
      this.icon = toStrokeIcon(icon, JBColor.namedColor(if (isActive) "Panel.foreground" else "Panel.disabledForeground"))
    }
  }

  override fun updateUI() {
    super.updateUI()
    updateIcon()
    updateIconInsets()
  }

  fun updateIconInsets() {
    setIconInsets(computeIconInsets())
  }

  private fun computeIconInsets(): Insets {
    val baseInsets = JBUI.CurrentTheme.TitlePane.dialogButtonInsets()
    val headerBorder = (ComponentUtil.findParentByCondition(this) { it is DialogHeader } as? DialogHeader?)?.insets
    if (headerBorder == null) return baseInsets
    // If the header has its own border, it's not factored in for the "close" button,
    // and therefore we should compensate for that to align the buttons properly.
    baseInsets.top -= headerBorder.top
    baseInsets.bottom += headerBorder.top
    return baseInsets
  }
}
