// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DeprecatedCallableAddReplaceWith")

package com.intellij.util.flow

import com.intellij.platform.util.coroutines.flow.collectLatestUndispatched
import com.intellij.platform.util.coroutines.flow.debounceBatch
import com.intellij.platform.util.coroutines.flow.mapStateIn
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.platform.util.coroutines.flow.zipWithNext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration

@Deprecated("Moved to com.intellij.platform.util.coroutines.flow", level = DeprecationLevel.ERROR)
@Internal
fun <X> Flow<X>.throttle(timeMs: Long): Flow<X> {
  return throttle(timeMs)
}

@Deprecated("Moved to com.intellij.platform.util.coroutines.flow", level = DeprecationLevel.ERROR)
@Internal
suspend fun <X> SharedFlow<X>.collectLatestUndispatched(action: suspend (value: X) -> Unit) {
  collectLatestUndispatched(action)
}

@Deprecated("Moved to com.intellij.platform.util.coroutines.flow", level = DeprecationLevel.ERROR)
@Internal
fun <T, R> Flow<T>.zipWithNext(transform: suspend (a: T, b: T) -> R): Flow<R> {
  return zipWithNext(transform)
}

@Deprecated("Moved to com.intellij.platform.util.coroutines.flow", level = DeprecationLevel.ERROR)
@Internal
fun <T> Flow<T>.zipWithNext(): Flow<Pair<T, T>> {
  return zipWithNext()
}

@Deprecated("Moved to com.intellij.platform.util.coroutines.flow", level = DeprecationLevel.ERROR)
@Internal
fun <T> Flow<T>.debounceBatch(duration: Duration): Flow<List<T>> {
  return debounceBatch(duration)
}

@ApiStatus.Experimental
@Deprecated("Moved to com.intellij.platform.util.coroutines.flow", level = DeprecationLevel.ERROR)
@Internal
fun <T, M> StateFlow<T>.mapStateIn(
  coroutineScope: CoroutineScope,
  started: SharingStarted = SharingStarted.Eagerly,
  transform: (value: T) -> M
): StateFlow<M> {
  return mapStateIn(coroutineScope, started, transform)
}
