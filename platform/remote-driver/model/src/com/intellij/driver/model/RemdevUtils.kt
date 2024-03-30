package com.intellij.driver.model

import com.intellij.driver.model.transport.Ref

sealed interface RefDelegate<T : Any>

class LocalRefDelegate<T : Any>(val localValue: T) : RefDelegate<T>
class RemoteRefDelegate<T : Any>(val remoteRef: Ref) : RefDelegate<T>

fun <T : Any> RefDelegate<T>.unwrap(): T = when (this) {
  is LocalRefDelegate -> localValue
  is RemoteRefDelegate -> throw UnsupportedOperationException("Cannot get remote value")
}

fun <T : Any> List<RefDelegate<T>>.unwrap(): List<T> = map { it.unwrap() }

enum class RdTarget {
  FRONTEND_FIRST,
  FRONTEND_ONLY,
  BACKEND_ONLY,
}
