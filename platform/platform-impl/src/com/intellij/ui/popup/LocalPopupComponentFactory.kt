// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup

import com.intellij.concurrency.resetThreadContext
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

open class LocalPopupComponentFactory: PopupComponentFactory {
  override fun createPopupComponent(type: PopupComponentFactory.PopupType,
                                    owner: Component,
                                    content: Component,
                                    x: Int, y: Int,
                                    jbPopup: JBPopup): PopupComponent {
    if (type == PopupComponentFactory.PopupType.DIALOG) {
      return DialogPopupWrapper(owner, content, x, y, jbPopup)
    }

    val factory = PopupFactory.getSharedInstance()
    val oldType = PopupUtil.getPopupType(factory)

    if (type == PopupComponentFactory.PopupType.HEAVYWEIGHT) {
      PopupUtil.setPopupType(factory, 2)
    }
    val popup = factory.getPopup(owner, content, x, y)

    if (oldType >= 0) PopupUtil.setPopupType(factory, oldType)

    return AwtPopupWrapper(popup, jbPopup)
  }

  class DialogPopupWrapper(owner: Component, content: Component, x: Int, y: Int, jbPopup: JBPopup) : PopupComponent {
    private val myDialog: JDialog
    private var myRequestFocus = true
    override fun setRequestFocus(requestFocus: Boolean) {
      myRequestFocus = requestFocus
    }

    init {
      require(UIUtil.isShowing(owner)) { "Popup owner must be showing, owner " + owner.javaClass }
      val window = ComponentUtil.getWindow(owner)
      myDialog = when (window) {
        is Frame -> JDialog(window as Frame?)
        is Dialog -> JDialog(window as Dialog?)
        else -> JDialog()
      }

      myDialog.contentPane.layout = BorderLayout()
      myDialog.contentPane.add(content, BorderLayout.CENTER)
      myDialog.rootPane.putClientProperty(JBPopup.KEY, jbPopup)
      myDialog.rootPane.windowDecorationStyle = JRootPane.NONE
      myDialog.isUndecorated = true
      myDialog.background = UIUtil.getPanelBackground()
      myDialog.pack()
      myDialog.setLocation(x, y)
    }

    override fun getWindow(): Window = myDialog

    override fun hide(dispose: Boolean) {
      myDialog.isVisible = false
      if (dispose) {
        myDialog.dispose()
        myDialog.rootPane.putClientProperty(JBPopup.KEY, null)
      }
    }

    override fun show() {
      if (!myRequestFocus) {
        myDialog.focusableWindowState = false
      }
      AwtPopupWrapper.fixFlickering(myDialog, false)
      myDialog.addWindowListener(object : WindowAdapter() {
        override fun windowClosed(e: WindowEvent) {
          //A11YFix.invokeFocusGained(myDialog);
          myDialog.removeWindowListener(this)
        }
      })
      myDialog.isVisible = true
      AwtPopupWrapper.fixFlickering(myDialog, true)
      SwingUtilities.invokeLater {
        myDialog.focusableWindowState = true
      }
    }
  }

  class AwtPopupWrapper(
    private val popup: Popup,
    private val myJBPopup: JBPopup
    //TODO[tav]: should we call A11YFix.invokeFocusGained(getWindow()) on window closing?
  ) : PopupComponent {
    override fun hide(dispose: Boolean) {
      val window = window
      if (!dispose) {
        // `resetThreadContext` here is needed because `setVisible` runs eventloop
        // IJPL-161712
        resetThreadContext {
          window?.isVisible = false
        }
        return
      }
      val rootPane = (window as? RootPaneContainer)?.rootPane
      popup.hide()
      DialogWrapper.cleanupRootPane(rootPane)
      DialogWrapper.cleanupWindowListeners(window)
    }

    override fun show() {
      val window = window
      if (window != null) {
        fixFlickering(window, false)
      }
      popup.show()
      if (window != null) {
        fixFlickering(window, true)
        if (window is JWindow) {
          window.rootPane.putClientProperty(JBPopup.KEY, myJBPopup)
        }
      }
    }

    override fun getWindow(): Window? {
      return (popup as? HeavyWeightPopup)?.window as? JWindow
    }

    override fun setRequestFocus(requestFocus: Boolean) {}

    companion object {
      internal fun fixFlickering(window: Window, opaque: Boolean) {
        try {
          if (StartupUiUtil.isUnderDarcula &&
              SystemInfoRt.isMac &&
              Registry.`is`("darcula.fix.native.flickering", false)) {
            window.opacity = if (opaque) 1.0f else 0.0f
          }
        }
        catch (ignore: Exception) {}
      }
    }
  }
}