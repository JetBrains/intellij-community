// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef.menu

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.awt.RelativePoint
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.callback.CefRunContextMenuCallback
import org.cef.handler.CefContextMenuHandlerAdapter
import java.awt.Point
import javax.swing.SwingUtilities

internal class CefContextMenuRunner : CefContextMenuHandlerAdapter() {
  override fun runContextMenu(browser: CefBrowser, frame: CefFrame, params: CefContextMenuParams, model: CefMenuModel, callback: CefRunContextMenuCallback): Boolean {
    if (model.count <= 0) {
      closePopup()
      return true
    }

    val menuAdapter = JBCefMenuAdapter(model) { selectedItem ->
      if (selectedItem == null) {
        callback.cancel()
      }
      else {
        callback.Continue(selectedItem.commandId, 0)
      }
    }

    val popupCoordinates = Point(params.xCoord, params.yCoord)

    SwingUtilities.invokeLater {
      myCurrentPopup = JBPopupFactory.getInstance().createListPopup(menuAdapter).apply {
        setRequestFocus(false)
        show(RelativePoint(browser.uiComponent, popupCoordinates))
      }
    }

    return true
  }

  override fun onContextMenuDismissed(browser: CefBrowser?, frame: CefFrame?) {
    closePopup()
  }

  private fun closePopup() {
    SwingUtilities.invokeLater {
      mySelectionCallback?.cancel()
      mySelectionCallback = null

      myCurrentPopup?.cancel()
      myCurrentPopup = null
    }
  }

  fun isShowing(): Boolean = myCurrentPopup != null

  private var myCurrentPopup: ListPopup? = null
  private var mySelectionCallback: CefRunContextMenuCallback? = null
}