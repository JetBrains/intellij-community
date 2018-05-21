// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderUnprotected

internal inline fun <V : Any, R> UserDataHolderUnprotected.withKey(key: Key<V>, value: V?, crossinline computation: () -> R): R {
  val previous = this[key]
  if (value == previous) {
    return computation()
  }
  try {
    this[key] = value
    return computation()
  }
  finally {
    this[key] = previous
  }
}

internal operator fun <V : Any> UserDataHolderUnprotected.get(key: Key<V>): V? {
  return getUserDataUnprotected(key)
}

internal operator fun UserDataHolderUnprotected.get(key: Key<Boolean>): Boolean {
  return getUserDataUnprotected(key) == true
}

internal operator fun <V : Any> UserDataHolderUnprotected.set(key: Key<V>, value: V?) {
  putUserDataUnprotected(key, value)
}

internal fun <V : Any> UserDataHolderUnprotected.getAndReset(key: Key<V>): V? {
  val result = this[key]
  this[key] = null
  return result
}
