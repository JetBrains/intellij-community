// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.concurrency

import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus

internal fun isHandlerObsolete(handler: Any): Boolean {
  return handler is Obsolescent && handler.isObsolete
}

internal interface CompletablePromise<T> : Promise<T> {
  fun setResult(t: T?)
  fun setError(error: Throwable): Boolean
}

@ApiStatus.Internal
internal class MessageError(message: String, isLog: Boolean) : RuntimeException(message) {
  val log: ThreeState = ThreeState.fromBoolean(isLog)

  override fun fillInStackTrace(): MessageError = this
}

@ApiStatus.Internal
fun isMessageError(exception: Exception): Boolean {
  return exception is MessageError
}

internal class PromiseValue<T> private constructor(val result: T?, val error: Throwable?) {
  companion object {
    @JvmStatic
    fun <T : Any?> createFulfilled(result: T?): PromiseValue<T> {
      return PromiseValue(result, null)
    }

    fun <T : Any?> createRejected(error: Throwable?): PromiseValue<T> {
      return PromiseValue(null, error)
    }
  }

  val state: Promise.State
    get() = if (error == null) Promise.State.SUCCEEDED else Promise.State.REJECTED

  fun getResultOrThrowError(): T? {
    return when (error) {
      null -> result
      else -> throw error.cause ?: error
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val value = other as PromiseValue<*>
    return result == value.result && error == value.error
  }

  override fun hashCode(): Int {
    return 31 * (result?.hashCode() ?: 0) + (error?.hashCode() ?: 0)
  }
}