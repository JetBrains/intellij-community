// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.wm.WindowInfo

internal data class ToolWindowEntry(val stripeButton: StripeButton,
                                    val toolWindow: ToolWindowImpl,
                                    val disposable: Disposable) {
  var floatingDecorator: FloatingDecorator? = null
  var windowedDecorator: FrameWrapper? = null
  var balloon: Balloon? = null

  val id: String
    get() = toolWindow.id

  val readOnlyWindowInfo: WindowInfo
    get() = toolWindow.windowInfo

  fun applyWindowInfo(newInfo: WindowInfo) {
    toolWindow.applyWindowInfo(newInfo)
    // must be applied _after_ updating tool window layout info
    stripeButton.apply(newInfo)
  }
}