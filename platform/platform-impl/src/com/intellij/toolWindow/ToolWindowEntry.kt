// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.impl.FloatingDecorator
import com.intellij.openapi.wm.impl.ToolWindowImpl
import javax.swing.Icon
import javax.swing.JComponent

internal interface StripeButtonManager {
  val id: String
  val windowDescriptor: WindowInfo

  fun updateState(toolWindow: ToolWindowImpl)

  fun updatePresentation()

  fun updateIcon(icon: Icon?)

  fun remove()

  fun getComponent(): JComponent
}

internal class ToolWindowEntry(stripeButton: StripeButtonManager?,
                               @JvmField val toolWindow: ToolWindowImpl,
                               @JvmField val disposable: Disposable) {
  var stripeButton: StripeButtonManager? = stripeButton
    set(value) {
      if (value == null) {
        assert(field != null)
      }
      else {
        assert(field == null)
      }
      field = value
    }

  @JvmField
  var floatingDecorator: FloatingDecorator? = null

  @JvmField
  var windowedDecorator: FrameWrapper? = null

  @JvmField
  var balloon: Balloon? = null

  val id: String
    get() = toolWindow.id

  val readOnlyWindowInfo: WindowInfo
    get() = toolWindow.windowInfo

  fun removeStripeButton() {
    val stripeButton = stripeButton ?: return
    this.stripeButton = null
    stripeButton.remove()
  }

  fun applyWindowInfo(newInfo: WindowInfo) {
    toolWindow.applyWindowInfo(newInfo)
    // must be applied _after_ updating tool window layout info
    val stripeButton = stripeButton ?: return
    stripeButton.updateState(toolWindow)
  }
}