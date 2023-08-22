// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

sealed interface State {
  object UnknownYet : State

  sealed interface DefinedState<out T> : State

  /**
   * Use [NotEnoughInformationCause] to designate a situation when there is not enough data to succeed the recovery
   * (though the process went normal). Throw [VfsRecoveryException] if an exception occurs during the recovery process and it is
   * considered not normal.
   */
  class NotAvailable(
    val cause: NotAvailableException = UnspecifiedNotAvailableException
  ) : DefinedState<Nothing> {
    constructor(message: String, cause: Throwable? = null) : this(NotAvailableException(message, cause))

    override fun toString(): String = "N/A ($cause)"
  }

  class Ready<T>(val value: T) : DefinedState<T> {
    override fun toString(): String = value.toString()
  }

  companion object {
    fun notEnoughInformation(message: String, cause: Throwable? = null): NotAvailable = NotAvailable(NotEnoughInformationCause(message, cause))
    fun <T> DefinedState<T>.get(): T = mapCases({ throw AssertionError("value expected to be available", it) }) { it }
    fun <T> DefinedState<T>.getOrNull(): T? = mapCases({ null }) { it }
    fun <T> DefinedState<T>.getOrDefault(default: () -> T): T = mapCases({ default() }) { it }

    inline fun <T, R> DefinedState<T>.mapCases(onNotAvailable: (cause: NotAvailableException) -> R,
                                               onReady: (value: T) -> R): R = when (this) {
      is Ready<T> -> onReady(value)
      is NotAvailable -> onNotAvailable(cause)
    }

    inline fun <T, R> DefinedState<T>.fmap(f: (T) -> R): DefinedState<R> = when (this) {
      is NotAvailable -> this
      is Ready<T> -> Ready(f(value))
    }

    inline fun <T, R> DefinedState<T>.bind(f: (T) -> DefinedState<R>): DefinedState<R> = when (this) {
      is NotAvailable -> this
      is Ready<T> -> f(value)
    }

    inline fun <T> DefinedState<T>?.orIfNotAvailable(other: () -> DefinedState<T>): DefinedState<T> = when (this) {
      is Ready -> this
      is NotAvailable -> other()
      null -> other()
    }
  }
}