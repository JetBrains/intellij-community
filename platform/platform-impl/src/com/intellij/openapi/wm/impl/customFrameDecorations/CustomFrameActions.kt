// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AppUIUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUIScale
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.*
import javax.swing.*
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener

class CustomFrameActions private constructor(var root: JRootPane) : Disposable {
  companion object {
    fun createSimple(root: JRootPane): CustomFrameActions = CustomFrameActions(root).apply {
      buttonPanes = CustomFrameTitleButtons.create(myCloseAction)
      init()
    }

    fun create(root: JRootPane) = CustomFrameActions(root).apply {
      if (root.windowDecorationStyle == JRootPane.FRAME) {
        buttonPanes = ResizableCustomFrameTitleButtons.create(myCloseAction,
                                                              myRestoreAction, myIconifyAction,
                                                              myMaximizeAction)

      }

      init()
    }
  }

  private var myState: Int = -100
  private lateinit var buttonPanes: CustomFrameTitleButtons
  private lateinit var productIcon: JComponent

  private val myCloseAction: Action = CustomFrameAction("Close", AllIcons.Windows.CloseSmall) { close() }
  private val myIconifyAction: Action = CustomFrameAction("Minimize", AllIcons.Windows.MinimizeSmall) { iconify() }
  private val myRestoreAction: Action = CustomFrameAction("Restore", AllIcons.Windows.RestoreSmall) { restore() }
  private val myMaximizeAction: Action = CustomFrameAction("Maximize", AllIcons.Windows.MaximizeSmall) { maximize() }

  private var frame: Frame? = null

  private val myWindowListener = object : WindowAdapter() {
    override fun windowActivated(e: WindowEvent?) {
      buttonPanes.isSelected = true
    }

    override fun windowDeactivated(e: WindowEvent?) {
      buttonPanes.isSelected = false
    }
  }

  fun init() {
    val ancestorListener = object : AncestorListener {
      override fun ancestorAdded(event: AncestorEvent?) {
        updateAncestor()
      }

      override fun ancestorRemoved(event: AncestorEvent) {
        updateAncestor()
      }

      override fun ancestorMoved(event: AncestorEvent?) {
      }
    }

    productIcon = createProductIcon()

    buttonPanes.getView().addAncestorListener(ancestorListener)
    Disposer.register(this, Disposable { buttonPanes.getView().removeAncestorListener(ancestorListener) })
    updateAncestor()
  }

  fun getButtonPaneView(): JComponent = buttonPanes.getView()
  fun getProductIcon(): JComponent = productIcon

  private fun updateAncestor() {
    val windowAncestor = SwingUtilities.getWindowAncestor(buttonPanes.getView())
    if (!Objects.equals(windowAncestor, frame)) {
      frame?.removeWindowListener(myWindowListener)
    }
    if (windowAncestor is Frame) frame = windowAncestor
    else {
      frame = null
      return
    }

    windowAncestor.addWindowListener(myWindowListener)
    Disposer.register(this, Disposable { windowAncestor.removeWindowListener(myWindowListener) })

    setExtendedState(windowAncestor.extendedState)
  }

  override fun dispose() {

  }

  private fun close() {
    Disposer.dispose(this)
    frame?.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
  }

  private fun iconify() {
    setExtendedState(myState or Frame.ICONIFIED)
  }

  private fun maximize() {
    setExtendedState(myState or Frame.MAXIMIZED_BOTH)
  }

  private fun restore() {
    if (myState and Frame.ICONIFIED != 0) {
      setExtendedState(myState and Frame.ICONIFIED.inv())
    }
    else {
      setExtendedState(myState and Frame.MAXIMIZED_BOTH.inv())
    }
  }

  private fun setExtendedState(state: Int) {

    if (myState == state) {
      return
    }

    myState = state

    val fm = frame ?: return

    fm.extendedState = state
    if (fm.isResizable) {
      if (state and Frame.MAXIMIZED_BOTH != 0) {
        myMaximizeAction.isEnabled = false
        myRestoreAction.isEnabled = true
      }
      else {
        myMaximizeAction.isEnabled = true
        myRestoreAction.isEnabled = false
      }
    }
    else {
      myMaximizeAction.isEnabled = false
      myRestoreAction.isEnabled = false
    }

    myCloseAction.isEnabled = true
    myState = state
    buttonPanes.updateVisibility()
  }

  private fun createProductIcon(): JComponent {
    val myMenuBar = object : JMenuBar() {
      private val myIconProvider = JBUIScale.ScaleContext.Cache { ctx ->
        ObjectUtils.notNull(
          AppUIUtil.loadHiDPIApplicationIcon(ctx, 16), AllIcons.Icon_small)
      }

      private val icon: Icon
        get() {
          if (frame == null) return AllIcons.Icon_small

          val ctx = JBUIScale.ScaleContext.create(frame)
          ctx.overrideScale(JBUIScale.ScaleType.USR_SCALE.of(1.0))
          return myIconProvider.getOrProvide(ctx) ?: AllIcons.Icon_small
        }

      override fun getPreferredSize(): Dimension {
        return minimumSize
      }

      override fun getMinimumSize(): Dimension {
        return Dimension(icon.iconWidth, icon.iconHeight)
      }

      override fun paint(g: Graphics?) {
        icon.paintIcon(this, g, 0, 0)
      }
    }

    val menu = object : JMenu() {
      override fun getPreferredSize(): Dimension {
        return myMenuBar.preferredSize
      }
    }
    myMenuBar.add(menu)

    myMenuBar.isOpaque = false
    menu.isFocusable = false
    menu.isBorderPainted = true

    if (root.windowDecorationStyle == JRootPane.FRAME) {
      addMenuItems(menu)
    }
    return myMenuBar
  }

  private fun addMenuItems(menu: JMenu) {
    menu.add(myRestoreAction)
    menu.add(myIconifyAction)
    if (Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
      menu.add(myMaximizeAction)
    }

    menu.add(JSeparator())

    val closeMenuItem = menu.add(myCloseAction)
    closeMenuItem.font = JBUI.Fonts.label().deriveFont(Font.BOLD)
  }
}

private class CustomFrameAction(name: String, icon: Icon, val action: () -> Unit) : AbstractAction(name, icon) {
  override fun actionPerformed(e: ActionEvent) = action()
}


