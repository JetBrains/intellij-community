// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.util.ObjectUtils

/**
 * Get or create the user-data under the current key.
 * This operation is atomic as it relies on the atomic [UserDataHolderEx.putUserDataIfAbsent]
 */
inline fun <T : Any> UserDataHolderEx.getOrCreateUserData(key: Key<T>, producer: () -> T): T {
  getUserData(key)?.let { return it }
  return putUserDataIfAbsent(key, producer())
}

/**
 * Get or create the user-data under the current key.
 * @param producer Tries to create the value of returns `null` if the value cannot be created.
 * This operation is atomic as it relies on the atomic [UserDataHolderEx.putUserDataIfAbsent]
 */
inline fun <T> UserDataHolderEx.getOrMaybeCreateUserData(key: Key<T>, producer: () -> T?): T? {
  getUserData(key)?.let { return it }
  val newValueOrNull = producer() ?: return null
  return putUserDataIfAbsent(key, newValueOrNull)
}

/**
 * Update the current user data based upon the current value.
 * Note: This method is considered to be thread safe as it relies on the atomic [UserDataHolderEx.replace].
 * Note: The [update] function could be called multiple times when many threads are trying to update the value at the same time.
 * @return The updated value (null if the update function returned null)
 */
inline fun <T : Any> UserDataHolderEx.updateUserData(key: Key<T>, update: (T?) -> T?): T? {
  while (true) {
    val existing = getUserData(key)
    val newValue = update(existing)
    if (replace(key, existing, newValue)) {
      return newValue
    }
  }
}

/**
 * Update the current user data based upon the current value.
 * Note: This method is considered to be thread safe as it relies on the atomic [UserDataHolderEx.replace].
 * Note: The [update] function could be called multiple times when many threads are trying to update the value at the same time.
 * @return The previous value, which got replaced by the update
 */
inline fun <T : Any> UserDataHolderEx.getAndUpdateUserData(key: Key<T>, update: (T?) -> T?): T? {
  while (true) {
    val existing = getUserData(key)
    val newValue = update(existing)
    if (replace(key, existing, newValue)) {
      return existing
    }
  }
}


/*
UNSAFE / DEPRECATED APIs
 */

/**
 * Warning: This method is not thread-safe: Use [UserDataHolderEx] based APIs instead
 * See: [UserDataHolderEx.getOrCreateUserData]
 */
inline fun <T> UserDataHolder.getOrCreateUserDataUnsafe(key: Key<T>, producer: () -> T): T {
  val existing = getUserData(key)
  if (existing != null) return existing

  val value = producer()
  putUserData(key, value)
  return value
}

/**
 * Warning: This method is not thread-safe: Use [UserDataHolderEx] based APIs instead
 * See: [UserDataHolderEx.getOrCreateUserData]
 */
@Deprecated("Use 'UserDataHolderEx' APIs instead", replaceWith = ReplaceWith("getOrCreateUserDataUnsafe(key, producer)"))
inline fun <T> UserDataHolder.getOrCreateUserData(key: Key<T>, producer: () -> T): T {
  return getOrCreateUserDataUnsafe(key, producer)
}

/**
 * Note: This method is not thread safe
 */
inline fun <T> UserDataHolder.nullableLazyValueUnsafe(key: Key<T>, producer: () -> T?): T? {
  val existing = getUserData(key)
  if (existing == ObjectUtils.NULL) return null
  if (existing != null) return existing

  val value = producer()
  @Suppress("UNCHECKED_CAST")
  putUserData(key, value ?: ObjectUtils.NULL as T)
  return value
}


/**
 * Warning: This method is not thread-safe: Use [UserDataHolderEx] based APIs instead
 */
@Deprecated("Use 'UserDataHolderEx' APIs instead", replaceWith = ReplaceWith("nullableLazyValueUnsafe(key, producer)"))
inline fun <T> UserDataHolder.nullableLazyValue(key: Key<T>, producer: () -> T?): T? {
  return nullableLazyValueUnsafe(key, producer)
}
