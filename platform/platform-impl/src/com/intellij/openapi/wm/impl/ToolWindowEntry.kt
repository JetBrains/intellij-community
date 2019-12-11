// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.Balloon

internal data class ToolWindowEntry(val stripeButton: StripeButton,
                                    val internalDecorator: InternalDecorator,
                                    val watcher: ToolWindowManagerImpl.ToolWindowFocusWatcher,
                                    val disposable: Disposable) {
  var floatingDecorator: FloatingDecorator? = null
  var windowedDecorator: WindowedDecorator? = null
  var balloon: Balloon? = null

  val readOnlyWindowInfo: WindowInfoImpl
    get() = internalDecorator.windowInfo
}