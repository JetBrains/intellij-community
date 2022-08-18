// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Experimental
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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

/**
 * Creates a new instance of [SuspendingLazy] that uses the specified initialization function [initializer].
 *
 * If the initializer is running, and coroutines suspended in [SuspendingLazy.getValue] are cancelled,
 * then the [initializer] gets also cancelled, and the next call to [SuspendingLazy.getValue] will start the initializer from scratch.
 *
 * Once the initialized completes, the [SuspendingLazy] is considered completed.
 * Once completed, hard references to [this], [initializerContext] and [initializer] are erased,
 * and the returned instance only references the result.
 *
 * If the [initializer] throws, the throwable is stored in the returned instance, and this [SuspendingLazy] is also considered completed.
 * Suspended and subsequent [SuspendingLazy.getValue] calls are resumed with the thrown instance.
 */
@Experimental
fun <T> CoroutineScope.suspendingLazy(
  initializerContext: CoroutineContext = EmptyCoroutineContext,
  initializer: suspend CoroutineScope.() -> T,
): SuspendingLazy<T> {
  return SuspendingLazyImpl(this, initializerContext, initializer)
}
