// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder

internal inline fun <V : Any, R> UserDataHolder.withKey(key: Key<V>, value: V?, crossinline computation: () -> R): R {
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

internal operator fun UserDataHolder.get(key: Key<Boolean>): Boolean = getUserData(key) == true

internal operator fun <V : Any> UserDataHolder.get(key: Key<V>): V? = getUserData(key)

internal operator fun <V : Any> UserDataHolder.set(key: Key<V>, value: V?): Unit = putUserData(key, value)
