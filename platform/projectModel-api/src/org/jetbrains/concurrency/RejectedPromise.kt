// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency

import com.intellij.util.Consumer
import com.intellij.util.Function
import java.util.concurrent.Future

import java.util.concurrent.TimeUnit

internal class RejectedPromise<T>(private val error: Throwable) : Promise<T>, Future<T> {
  override fun getState() = Promise.State.REJECTED

  override fun done(done: Consumer<in T>) = this

  override fun processed(child: Promise<in T>): Promise<T> {
    if (child is AsyncPromise) {
      child.setError(error)
    }
    return this
  }

  override fun rejected(rejected: Consumer<Throwable>): Promise<T> {
    if (!isObsolete(rejected)) {
      rejected.consume(error)
    }
    return this
  }

  override fun processed(processed: Consumer<in T>): RejectedPromise<T> {
    processed.consume(null)
    return this
  }

  @Suppress("UNCHECKED_CAST")
  override fun <SUB_RESULT> then(handler: Function<in T, out SUB_RESULT>) = this as Promise<SUB_RESULT>

  @Suppress("UNCHECKED_CAST")
  override fun <SUB_RESULT> thenAsync(handler: Function<in T, Promise<SUB_RESULT>>) = this as Promise<SUB_RESULT>

  override fun blockingGet(timeout: Int, timeUnit: TimeUnit): T? {
    throw error
  }

  override fun cancel(mayInterruptIfRunning: Boolean) = false

  override fun get(): T? = null

  override fun get(timeout: Long, unit: TimeUnit?): T? = null

  override fun isCancelled() = error == OBSOLETE_ERROR

  override fun isDone() = state != Promise.State.PENDING
}