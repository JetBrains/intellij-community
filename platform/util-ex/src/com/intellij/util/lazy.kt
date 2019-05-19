// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

/**
 * Creates a new instance of the [Lazy] that uses the specified initialization function [initializer].
 *
 * If the initialization of a value recurs or throws an exception,
 * it will attempt to reinitialize the value at next access.
 *
 * The returned instance uses the specified [recursionKey] object as computation ID
 * to check if [this computation][initializer] is already running on the current thread.
 * When the [recursionKey] is not specified the instance uses itself as computation ID.
 *
 * @see com.intellij.openapi.util.RecursionGuard.doPreventingRecursion
 */
fun <T> recursionSafeLazy(recursionKey: Any? = null, initializer: () -> T): Lazy<T?> {
  return RecursionPreventingSafePublicationLazy(recursionKey, initializer)
}

fun <T> lazyPub(initializer: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.PUBLICATION, initializer)