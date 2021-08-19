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
 * and subscribe patched listener with provided disposable into your event system.
 * @param parentDisposable is subscription disposable.
 * This uses for early unsubscription when listener is called less than ttl times.
 *
 * @see com.intellij.openapi.observable.operations.subscribe(Int, () -> Unit, (() -> Unit, Disposable) -> Unit, Disposable)
 */
private fun subscribe(ttl: Int, subscribe: (() -> Unit, Disposable) -> Unit, parentDisposable: Disposable) {
  require(ttl > 0)
  val disposable = Disposer.newDisposable(parentDisposable, "TTL subscription")
  val ttlCounter = AtomicInteger(ttl)
  subscribe({
    if (ttlCounter.decrementAndGet() == 0) {
      Disposer.dispose(disposable)
    }
  }, disposable)
}

/**
 * Subscribes listener without arguments.
 * Unsubscribes when [parentDisposable] is disposed or [ttl] is out.
 *
 * @param ttl time to live
 * @param listener is a listener function that will be called [ttl] times
 * @param subscribe subscribes patched listener with provided disposable into your event system.
 * @param parentDisposable is subscription disposable.
 *
 * @see com.intellij.openapi.observable.operations.subscribe(Int, (() -> Unit, Disposable) -> Unit, Disposable)
 */
@JvmName("subscribe0")
fun subscribe(
  ttl: Int,
  listener: () -> Unit,
  subscribe: (() -> Unit, Disposable) -> Unit,
  parentDisposable: Disposable
) = subscribe(ttl, { prependListener, disposable ->
  subscribe({
    prependListener()
    listener()
  }, disposable)
}, parentDisposable)

/**
 * Subscribes listener with one argument.
 * Unsubscribes when [parentDisposable] is disposed or [ttl] is out.
 *
 * @see com.intellij.openapi.observable.operations.subscribe(Int, () -> Unit, (() -> Unit, Disposable) -> Unit, Disposable)
 */
@JvmName("subscribe1")
fun <A1> subscribe(
  ttl: Int,
  listener: (A1) -> Unit,
  subscribe: ((A1) -> Unit, Disposable) -> Unit,
  parentDisposable: Disposable
) = subscribe(ttl, { prependListener, disposable ->
  subscribe({ a1 ->
    prependListener()
    listener(a1)
  }, disposable)
}, parentDisposable)

/**
 * Subscribes listener with two arguments.
 * Unsubscribes when [parentDisposable] is disposed or [ttl] is out.
 *
 * @see com.intellij.openapi.observable.operations.subscribe(Int, () -> Unit, (() -> Unit, Disposable) -> Unit, Disposable)
 */
@JvmName("subscribe2")
fun <A1, A2> subscribe(
  ttl: Int,
  listener: (A1, A2) -> Unit,
  subscribe: ((A1, A2) -> Unit, Disposable) -> Unit,
  parentDisposable: Disposable
) = subscribe(ttl, { prependListener, disposable ->
  subscribe({ a1, a2 ->
    prependListener()
    listener(a1, a2)
  }, disposable)
}, parentDisposable)

