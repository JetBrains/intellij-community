// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicReference

@Internal
@Experimental
fun Job.cancelOnDispose(disposable: Disposable) {
  val childDisposable = Disposable { cancel("disposed") }
  Disposer.register(disposable, childDisposable)
  invokeOnCompletion {
    Disposer.dispose(childDisposable)
  }
}

@Internal
@Experimental
@Deprecated("Use `cancelOnDispose`", ReplaceWith("cancelOnDispose(disposable)"))
fun Job.cancelOnDispose(disposable: Disposable, disposeOnCompletion: Boolean = true) {
  cancelOnDispose(disposable)
}

/**
 * Attaches [this] disposable to a scope [cs] so that when [cs] is canceled, [this] is also disposed.
 *
 * When [this] disposable is disposed from another place [cs] won't be touched.
 */
fun Disposable.disposeOnCompletion(cs: CoroutineScope) {
  val disposableRef = AtomicReference<Disposable?>(this)
  val disposableHandle = cs.coroutineContext.job.invokeOnCompletion {
    disposableRef.getAndSet(null)?.let {
      Disposer.dispose(it)
    }
  }

  val registered = Disposer.tryRegister(this) {
    disposableRef.getAndSet(null)?.let {
      disposableHandle.dispose()
    }
  }
  if (!registered) disposableHandle.dispose()
}

/**
 * This function is deprecated to emphasize that the disposable does not become a child of the scope.
 * - Its disposal happens out of scope, after the scope is completed. The scope does not wait for the disposal.
 * - The disposal failure does not cancel the scope.
 */
@Deprecated("Use `disposeOnCompletion` instead", ReplaceWith("disposeOnCompletion(cs)"))
fun Disposable.attachAsChildTo(cs: CoroutineScope) {
  disposeOnCompletion(cs)
}

/**
 * Creates a disposable that will be disposed on [this] scope cancellation.
 *
 * Manual disposal of a created Disposable is also possible and doesn't touch [this] scope.
 */
fun CoroutineScope.asDisposable(): Disposable {
  return Disposer.newDisposable("Disposable from scope: $this").also { it.attachAsChildTo(this) }
}