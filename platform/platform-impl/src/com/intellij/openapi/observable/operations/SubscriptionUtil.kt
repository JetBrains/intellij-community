// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("SubscriptionUtil")
package com.intellij.openapi.observable.operations

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Subscribed listener will be automatically unsubscribed when [ttl] is out.
 * This is maximum common code for subscription with TTL.
 * Subscribe functions cannot be generalized, because listener function can have arguments.
 *
 * @param ttl is number of listener calls which should be passed to unsubscribe listener
 * @param subscribe is subscribing function that should prepend lambda argument into listener function
 * and subscribe patched listener with provided disposable.
 * @return subscription disposable.
 */
private fun subscribe(ttl: Int, subscribe: (() -> Unit, Disposable) -> Unit): Disposable {
  require(ttl > 0)
  val parentDisposable = Disposer.newDisposable()
  val ttlCounter = AtomicInteger(ttl)
  subscribe({
    if (ttlCounter.decrementAndGet() == 0) {
      Disposer.dispose(parentDisposable)
    }
  }, parentDisposable)
  return parentDisposable
}

@JvmName("subscribe0")
fun <L> subscribe(ttl: Int, listener: () -> Unit, wrap: (() -> Unit) -> L, subscribe: (L, Disposable) -> Unit) =
  subscribe(ttl) { prependListener, disposable ->
    subscribe(wrap {
      prependListener()
      listener()
    }, disposable)
  }

@JvmName("subscribe1")
fun <L, A1> subscribe(ttl: Int, listener: (A1) -> Unit, wrap: ((A1) -> Unit) -> L, subscribe: (L, Disposable) -> Unit) =
  subscribe(ttl) { prependListener, disposable ->
    subscribe(wrap { a1 ->
      prependListener()
      listener(a1)
    }, disposable)
  }

@JvmName("subscribe2")
fun <L, A1, A2> subscribe(ttl: Int, listener: (A1, A2) -> Unit, wrap: ((A1, A2) -> Unit) -> L, subscribe: (L, Disposable) -> Unit) =
  subscribe(ttl) { prependListener, disposable ->
    subscribe(wrap { a1, a2 ->
      prependListener()
      listener(a1, a2)
    }, disposable)
  }
