// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class OperationResult<out T : Any> private constructor(
  private val valueNullable: T?,
  val exceptionClass: String
) {
  val hasValue get() = valueNullable != null
  val value get() = valueNullable!!

  infix fun <R: Any> fmap(action: (T) -> R): OperationResult<R> {
    if (hasValue) return fromValue(action(value))
    return fromException(exceptionClass)
  }

  companion object {
    fun <T : Any> fromValue(value: T): OperationResult<T> =
      OperationResult(value, "")

    fun fromException(exceptionClass: String) =
      OperationResult(null, exceptionClass)

    /*
     * Serialization of OperationResult<T>:
     * this class is used only with T in {Unit, Int (with value >= 0), Boolean} and exception class name gets enumerated,
     * so it is possible to serialize it using only one Int field (4 bytes)
     */
    const val SIZE_BYTES = Int.SIZE_BYTES

    inline fun <reified T: Any> OperationResult<T>.serialize(enumerator: (String) -> Int): Int {
      if (!hasValue) {
        val enum = enumerator(exceptionClass)
        assert(enum >= 0)
        return -enum - 1
      }
      return when (T::class) {
        Int::class -> {
          assert(value as Int >= 0)
          value as Int
        }
        Boolean::class -> {
          if (value as Boolean) { 1 } else { 0 }
        }
        Unit::class -> 0
        else -> throw IllegalStateException("OperationResult is not designed to be used with type-parameter " + T::class.java.name)
      }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T: Any> deserialize(data: Int, deenumerator: (Int) -> String): OperationResult<T> {
      if (data < 0) {
        val enumVal = -(data + 1)
        val exceptionClass = deenumerator(enumVal)
        return fromException(exceptionClass)
      }
      return when (T::class) {
        Int::class -> {
          fromValue(data) as OperationResult<T>
        }
        Boolean::class -> {
          assert(data == 0 || data == 1)
          fromValue(data == 1) as OperationResult<T>
        }
        Unit::class -> fromValue(Unit) as OperationResult<T>
        else -> throw IllegalStateException("OperationResult is not designed to be used with type-parameter " + T::class.java.name)
      }
    }
  }
}

@OptIn(ExperimentalContracts::class)
inline fun <R : Any> catchResult(processor: (OperationResult<R>) -> Unit, body: () -> R): R {
  contract {
    callsInPlace(processor, InvocationKind.EXACTLY_ONCE)
    callsInPlace(body, InvocationKind.EXACTLY_ONCE)
  }
  return try {
    body().also {
      processor(OperationResult.fromValue(it))
    }
  }
  catch (e: Throwable) {
    processor(OperationResult.fromException(e.javaClass.name))
    throw e
  }
}
