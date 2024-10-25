// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicReference

@Internal
@Experimental
@JvmOverloads
fun Job.cancelOnDispose(disposable: Disposable, disposeOnCompletion: Boolean = true) {
  val childDisposable = Disposable { cancel("disposed") }
  Disposer.register(disposable, childDisposable)
  if (disposeOnCompletion) {
    job.invokeOnCompletion {
      Disposer.dispose(childDisposable)
    }
  }
}

/**
 * Attaches [this] disposable to a scope [cs] so that when [cs] is canceled, [this] is also disposed.
 *
 * When [this] disposable is disposed from another place [cs] won't be touched.
 */
fun Disposable.attachAsChildTo(cs: CoroutineScope) {
  val disposableRef = AtomicReference<Disposable?>(this)
  val job = cs.launch {
    try {
      awaitCancellation()
    }
    finally {
      disposableRef.getAndSet(null)?.let {
        Disposer.dispose(it)
      }
    }
  }
  val registered = Disposer.tryRegister(this) {
    disposableRef.getAndSet(null)?.let {
      job.cancel("disposed")
    }
  }
  if (!registered) job.cancel("disposable is already disposed")
}

/**
 * Creates a disposable that will be disposed on [this] scope cancellation.
 *
 * Manual disposal of a created Disposable is also possible and doesn't touch [this] scope.
 */
fun CoroutineScope.asDisposable(): Disposable {
  return Disposer.newDisposable("Disposable from scope: $this").also { it.attachAsChildTo(this) }
}