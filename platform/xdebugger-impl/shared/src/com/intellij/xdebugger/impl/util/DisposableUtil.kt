package com.intellij.xdebugger.impl.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun Disposable.onTermination(disposable: Disposable) = Disposer.register(this, disposable)

@ApiStatus.Internal
fun Disposable.onTermination(action: () -> Unit) = Disposer.register(this) { action() }

@ApiStatus.Internal
fun disposeInEdt(disposable: Disposable): Unit = runInEdt { Disposer.dispose(disposable) }

@ApiStatus.Internal
fun createEdtDisposable(parentDisposable: Disposable): Disposable {
  val result = Disposer.newDisposable()
  val isSuccess = Disposer.tryRegister(parentDisposable) {
    disposeInEdt(result)
  }
  if (!isSuccess) {
    disposeInEdt(result)
  }
  return result
}

val Disposable.isAlive @ApiStatus.Internal get() = !Disposer.isDisposed(this)

val Disposable.isNotAlive @ApiStatus.Internal get() = !isAlive
