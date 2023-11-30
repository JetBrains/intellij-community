// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.CancellationException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Specialized version of Result<T> from kotlin stdlib
 */
class OperationResult<out T : Any> private constructor(
  private val _value: Any
) {
  val isSuccess: Boolean get() = _value !is ExceptionalResult

  @Suppress("UNCHECKED_CAST")
  val value: T get() = _value as T

  @PublishedApi
  internal val exceptionEvidence: Int get() = (_value as ExceptionalResult).evidence

  override fun toString(): String = _value.toString()

  companion object {
    private class ExceptionalResult(
      val evidence: Int // evidence < 0
    ) {
      override fun toString(): String {
        return "ExceptionalResult(exceptionEvidence=$evidence)"
      }
    }

    fun <T : Any> fromValue(value: T): OperationResult<T> =
      OperationResult(value)

    fun fromException(exception: Throwable): OperationResult<Nothing> =
      fromException(encodeException(exception))

    @PublishedApi
    internal fun fromException(exceptionEvidence: Int): OperationResult<Nothing> =
      OperationResult(ExceptionalResult(exceptionEvidence))

    /**
     * @return value < 0
     */
    private fun encodeException(exception: Throwable): Int =
      exception.javaClass.name.sumOf { -it.code }.coerceIn(Int.MIN_VALUE, -1)

    /*
     * Serialization of OperationResult<T>:
     * this class is used only with T in {Unit, Int (with value >= 0), Boolean}, and exception class name gets encoded to negative Int range,
     * so it is possible to serialize it using only one Int field (4 bytes)
     */
    const val SIZE_BYTES: Int = Int.SIZE_BYTES

    inline fun <reified T : Any> OperationResult<T>.serialize(): Int {
      if (!isSuccess) {
        val evidence = exceptionEvidence
        assert(evidence < 0)
        return evidence
      }
      // XXX: kotlin can't properly optimize branch selection if T::class is used here (although T is reified),
      // but using java class fixes it
      return when (T::class.javaObjectType) {
        Int::class.javaObjectType -> {
          assert(value as Int >= 0)
          value as Int
        }
        Boolean::class.javaObjectType -> {
          if (value as Boolean) 1 else 0
        }
        Unit::class.javaObjectType -> 0
        else -> throw IllegalStateException("OperationResult is not designed to be used with type-parameter " + T::class.java.name)
      }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> deserialize(data: Int): OperationResult<T> {
      if (data < 0) {
        return fromException(data)
      }
      // XXX: kotlin can't properly optimize branch selection if T::class is used here (although T is reified),
      // but using java class fixes it
      return when (T::class.javaObjectType) {
        Int::class.javaObjectType -> {
          fromValue(data) as OperationResult<T>
        }
        Boolean::class.javaObjectType -> {
          assert(data == 0 || data == 1)
          fromValue(data == 1) as OperationResult<T>
        }
        Unit::class.javaObjectType -> fromValue(Unit) as OperationResult<T>
        else -> throw IllegalStateException("OperationResult is not designed to be used with type-parameter " + T::class.java.name)
      }
    }

    internal val LOG = Logger.getInstance(OperationResult::class.java)
  }
}

@OptIn(ExperimentalContracts::class)
internal inline fun <R : Any> catchResult(crossinline processor: (result: OperationResult<R>) -> Unit, body: () -> R): R {
  contract {
    callsInPlace(processor, InvocationKind.EXACTLY_ONCE)
    callsInPlace(body, InvocationKind.EXACTLY_ONCE)
  }
  val safeProcessor = { result: OperationResult<R> ->
    try {
      processor(result)
    }
    catch (e: Throwable) {
      OperationResult.LOG.error(AssertionError("operation result processor must not throw an exception", e))
    }
  }
  return try {
    body().also {
      safeProcessor(OperationResult.fromValue(it))
    }
  }
  catch (e: Throwable) {
    if (e is ProcessCanceledException || e is CancellationException) {
      // catchResult is currently used in write interceptors for VFS storages, cancellation of such operations is something strange
      OperationResult.LOG.error("unexpected cancellation exception in catchResult: $e")
    }
    safeProcessor(OperationResult.fromException(e))
    throw e
  }
}

internal inline infix fun <R : Any> (() -> R).catchResult(crossinline processor: (result: OperationResult<R>) -> Unit): R =
  catchResult(processor, this)
