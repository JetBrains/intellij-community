package com.intellij.xdebugger.impl.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer


internal fun Disposable.onTermination(disposable: Disposable) = Disposer.register(this, disposable)
internal fun Disposable.onTermination(action: () -> Unit) = Disposer.register(this) { action() }
internal val Disposable.isAlive get() = !Disposer.isDisposed(this)
internal val Disposable.isNotAlive get() = !isAlive