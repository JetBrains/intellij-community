// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.util

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import kotlin.properties.Delegates

class SingleValueModel<T>(initialValue: T) {
  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  var value by Delegates.observable<T>(initialValue) { _, _, _ ->
    changeEventDispatcher.multicaster.eventOccurred()
  }

  @CalledInAwt
  fun addValueChangedListener(disposable: Disposable, listener: () -> Unit) =
    changeEventDispatcher.addListener(object : SimpleEventListener {
      override fun eventOccurred() {
        listener()
      }
    }, disposable)

  @CalledInAwt
  fun addValueChangedListener(listener: () -> Unit) =
    changeEventDispatcher.addListener(object : SimpleEventListener {
      override fun eventOccurred() {
        listener()
      }
    })
}