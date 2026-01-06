// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isNotAlive


/**
 * Creates a lifetime corresponding to this disposable.
 * The lifetime will be terminated when this disposable is disposed.
 * If it is already disposed, this will return a terminated lifetime
 */
fun Disposable.createLifetime(): Lifetime = this.defineNestedLifetime().lifetime

/**
 * Creates a lifetime definition bounded by this disposable.
 * The lifetime will be terminated when this disposable is disposed.
 * If it is already disposed, this will return a terminated lifetime definition
 */
fun Disposable.defineNestedLifetime(): LifetimeDefinition {
  val lifetimeDefinition = Lifetime.Eternal.createNested()
  if (Disposer.isDisposed(this)) {
    lifetimeDefinition.terminate()
    return lifetimeDefinition
  }

  if (!Disposer.tryRegister(this) { lifetimeDefinition.terminate() })
    lifetimeDefinition.terminate()

  return lifetimeDefinition
}

/**
 * Attaches [this] disposable as a child to [lifetime] such as the disposable will be terminated when [lifetime] terminates.
 *
 * When the disposable is disposed of its own, there should be no leaks because the subscription to [lifetime] is also terminated on disposal of [this].
 */
fun Disposable.attachAsChildTo(lifetime: Lifetime) {
  val childLt = lifetime.createNested()
  if (!childLt.onTerminationIfAlive {
      Disposer.dispose(this)
    }) {
    Disposer.dispose(this)
    return
  }

  if (!Disposer.tryRegister(this) {
      childLt.terminate()
    }) {
    childLt.terminate()
  }
}

/**
 * Performs an action id this disposable has not been disposed yet.
 * The action receives a lifetime corresponding to this disposable.
 * @see createLifetime
 */
fun Disposable.doIfAlive(action: (Lifetime) -> Unit) {
  if (Disposer.isDisposed(this)) return

  val disposableLifetime = createLifetime()
  if (disposableLifetime.isNotAlive) return

  action(disposableLifetime)
}

/**
 * Creates a disposable that will be disposed when the given lifetime is terminated.
 * If the lifetime was already terminated, the returned disposable will be disposed too,
 */
fun Lifetime.createNestedDisposable(debugName: String = "lifetimeToDisposable"): Disposable {
  val d = Disposer.newDisposable(debugName).apply {
    attachAsChildTo(this@createNestedDisposable)
  }
  return d
}
