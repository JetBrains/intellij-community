// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CopyOnWriteArrayList

abstract class AbstractObservableProperty<T> : ObservableClearableProperty<T> {

  private val changeListeners = CopyOnWriteArrayList<(T) -> Unit>()
  private val resetListeners = CopyOnWriteArrayList<() -> Unit>()

  protected fun fireChangeEvent(value: T) {
    changeListeners.forEach { it(value) }
  }

  protected fun fireResetEvent() {
    resetListeners.forEach { it() }
  }

  override fun afterChange(listener: (T) -> Unit) {
    changeListeners.add(listener)
  }

  override fun afterReset(listener: () -> Unit) {
    resetListeners.add(listener)
  }

  override fun afterChange(listener: (T) -> Unit, parentDisposable: Disposable) {
    changeListeners.add(listener)
    Disposer.register(parentDisposable, Disposable { changeListeners.remove(listener) })
  }

  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable) {
    resetListeners.add(listener)
    Disposer.register(parentDisposable, Disposable { resetListeners.remove(listener) })
  }
}