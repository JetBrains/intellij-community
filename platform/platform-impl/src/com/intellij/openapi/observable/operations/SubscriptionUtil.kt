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
 * This function subscribes wrapped listener into your event system.
 * @param parentDisposable is subscription disposable.
 * This uses for early unsubscription when listener is called less than ttl times.
 *
 * @see subscribe(Int, (A1) -> Unit, ((A1) -> Unit) -> L, (L, Disposable) -> Unit, Disposable)
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
 * @see subscribe(Int, (A1) -> Unit, ((A1) -> Unit) -> L, (L, Disposable) -> Unit, Disposable)
 */
@JvmName("subscribe0")
fun <L> subscribe(
  ttl: Int,
  listener: () -> Unit,
  wrap: (() -> Unit) -> L,
  subscribe: (L, Disposable) -> Unit,
  parentDisposable: Disposable
) = subscribe(ttl, { prependListener, disposable ->
  subscribe(wrap {
    prependListener()
    listener()
  }, disposable)
}, parentDisposable)

/**
 * Subscribes listener with one argument.
 * Unsubscribes when [parentDisposable] is disposed or [ttl] is out.
 *
 * @param ttl time to live
 * @param listener is a listener function that will be called [ttl] times
 * @param wrap converts [listener] function into listener object, for example:
 * `
 * wrap = { listener: (Event) -> Unit ->
 *   object : Listener {
 *      fun onEvent(event: Event) = listener(event)
 *
 *      // Other listener function must have default implementation
 *      fun onStart() {}
 *      fun onFinish(status: S) {}
 *   }
 * }
 * `
 * @param subscribe subscribes wrapped listener into your event system.
 * @param parentDisposable is subscription disposable.
 *
 * @see subscribe(Int, (() -> Unit, Disposable) -> Unit, Disposable)
 */
@JvmName("subscribe1")
fun <L, A1> subscribe(
  ttl: Int,
  listener: (A1) -> Unit,
  wrap: ((A1) -> Unit) -> L,
  subscribe: (L, Disposable) -> Unit,
  parentDisposable: Disposable
) = subscribe(ttl, { prependListener, disposable ->
  subscribe(wrap { a1 ->
    prependListener()
    listener(a1)
  }, disposable)
}, parentDisposable)

/**
 * Subscribes listener with two arguments.
 * Unsubscribes when [parentDisposable] is disposed or [ttl] is out.
 *
 * @see subscribe(Int, (A1) -> Unit, ((A1) -> Unit) -> L, (L, Disposable) -> Unit, Disposable)
 */
@JvmName("subscribe2")
fun <L, A1, A2> subscribe(
  ttl: Int,
  listener: (A1, A2) -> Unit,
  wrap: ((A1, A2) -> Unit) -> L,
  subscribe: (L, Disposable) -> Unit,
  parentDisposable: Disposable
) = subscribe(ttl, { prependListener, disposable ->
  subscribe(wrap { a1, a2 ->
    prependListener()
    listener(a1, a2)
  }, disposable)
}, parentDisposable)
