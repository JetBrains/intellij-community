// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.util.ObjectUtils

inline fun <T> UserDataHolder.getOrCreateUserData(key: Key<T>, producer: () -> T): T {
  val existing = getUserData(key)
  if (existing != null) return existing

  val value = producer()
  putUserData(key, value)
  return value
}

inline fun <T> UserDataHolder.nullableLazyValue(key: Key<T>, producer: () -> T?): T? {
  val existing = getUserData(key)
  if (existing == ObjectUtils.NULL) return null
  if (existing != null) return existing

  val value = producer()
  @Suppress("UNCHECKED_CAST")
  putUserData(key, value ?: ObjectUtils.NULL as T)
  return value
}
