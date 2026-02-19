// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DeprecatedCallableAddReplaceWith")

package com.intellij.util.flow

import com.intellij.platform.util.coroutines.flow.debounceBatch
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration

@Deprecated("Moved to com.intellij.platform.util.coroutines.flow", level = DeprecationLevel.ERROR)
@Internal
fun <T> Flow<T>.debounceBatch(duration: Duration): Flow<List<T>> {
  return debounceBatch(duration)
}

