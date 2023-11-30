// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

inline fun <T> UserDataHolder.getOrCreateUserData(key: Key<T>, producer: () -> T): T {
  val existing = getUserData(key)
  if (existing != null) return existing

  val value = producer()
  putUserData(key, value)
  return value
}
