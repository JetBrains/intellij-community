// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.util.containers.DisposableWrapperList

abstract class AbstractObservableProperty<T> : ObservableClearableProperty<T> {

  private val changeListeners = DisposableWrapperList<(T) -> Unit>()
  private val resetListeners = DisposableWrapperList<() -> Unit>()

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
    changeListeners.add(listener, parentDisposable)
  }

  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable) {
    resetListeners.add(listener, parentDisposable)
  }
}