// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.operations

import com.intellij.openapi.Disposable

/**
 * Defines observation API for observable process:
 * without any modification functions that can change operation status [isOperationCompleted].
 */
interface ObservableOperationTrace {
  /**
   * Checks that operations is completed.
   */
  fun isOperationCompleted(): Boolean

  /**
   * Subscribes listener with TTL on operation start event.
   * Subscribed listener will be automatically unsubscribed when [ttl] is out or when [parentDisposable] is disposed.
   * [parentDisposable] uses for early unsubscription when listener is called less than [ttl] times.
   *
   * @param ttl is a number of listener calls which should be passed to unsubscribe listener.
   * @param listener is a listener function that will be called [ttl] times.
   * @param parentDisposable is a subscription disposable.
   */
  fun beforeOperation(ttl: Int, listener: () -> Unit, parentDisposable: Disposable)

  /**
   * Subscribes listener on operation start event that will never been unsubscribed.
   */
  fun beforeOperation(listener: () -> Unit)

  /**
   * Subscribes listener on operation start event that unsubscribed when [parentDisposable] is disposed.
   */
  fun beforeOperation(listener: () -> Unit, parentDisposable: Disposable)

  /**
   * Subscribes listener with TTL on operation finish event.
   *
   * @see beforeOperation(Int, () -> Unit, Disposable)
   */
  fun afterOperation(ttl: Int, listener: () -> Unit, parentDisposable: Disposable)

  /**
   * Subscribes listener on operation finish event that will never been unsubscribed.
   */
  fun afterOperation(listener: () -> Unit)

  /**
   * Subscribes listener on operation finish event that unsubscribed when [parentDisposable] is disposed.
   */
  fun afterOperation(listener: () -> Unit, parentDisposable: Disposable)
}