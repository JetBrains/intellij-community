// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.operations

import com.intellij.openapi.Disposable
import com.intellij.util.containers.DisposableWrapperList

abstract class AbstractObservableOperationTrace : ObservableOperationTrace {

  private val beforeOperationListeners = DisposableWrapperList<() -> Unit>()
  private val afterOperationListeners = DisposableWrapperList<() -> Unit>()

  protected fun fireOperationStarted() {
    beforeOperationListeners.forEach { it() }
  }

  protected fun fireOperationFinished() {
    afterOperationListeners.forEach { it() }
  }

  override fun beforeOperation(ttl: Int, listener: () -> Unit, parentDisposable: Disposable) {
    subscribe(ttl, listener, ::beforeOperation, parentDisposable)
  }

  override fun beforeOperation(listener: () -> Unit) {
    beforeOperationListeners.add(listener)
  }

  override fun beforeOperation(listener: () -> Unit, parentDisposable: Disposable) {
    beforeOperationListeners.add(listener, parentDisposable)
  }

  override fun afterOperation(ttl: Int, listener: () -> Unit, parentDisposable: Disposable) {
    subscribe(ttl, listener, ::afterOperation, parentDisposable)
  }

  override fun afterOperation(listener: () -> Unit) {
    afterOperationListeners.add(listener)
  }

  override fun afterOperation(listener: () -> Unit, parentDisposable: Disposable) {
    afterOperationListeners.add(listener, parentDisposable)
  }
}