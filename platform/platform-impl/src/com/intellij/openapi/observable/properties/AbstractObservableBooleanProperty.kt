// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CopyOnWriteArrayList

abstract class AbstractObservableBooleanProperty : AbstractObservableProperty<Boolean>(), BooleanProperty {

  private val setListeners = CopyOnWriteArrayList<() -> Unit>()

  protected fun fireChangeEvents(oldValue: Boolean, newValue: Boolean) {
    when {
      !oldValue && newValue -> fireSetEvent()
      oldValue && !newValue -> fireResetEvent()
    }
    fireChangeEvent(newValue)
  }

  protected fun fireSetEvent() {
    setListeners.forEach { it() }
  }

  override fun afterSet(listener: () -> Unit) {
    setListeners.add(listener)
  }

  override fun afterSet(listener: () -> Unit, parentDisposable: Disposable) {
    setListeners.add(listener)
    Disposer.register(parentDisposable, Disposable { setListeners.remove(listener) })
  }
}