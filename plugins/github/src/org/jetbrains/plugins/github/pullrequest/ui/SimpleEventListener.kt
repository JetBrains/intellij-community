// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import java.util.*

interface SimpleEventListener : EventListener {
  fun eventOccurred()

  companion object {
    fun addDisposableListener(dispatcher: EventDispatcher<SimpleEventListener>, disposable: Disposable, listener: () -> Unit) {
      dispatcher.addListener(object : SimpleEventListener {
        override fun eventOccurred() {
          listener()
        }
      }, disposable)
    }
  }
}