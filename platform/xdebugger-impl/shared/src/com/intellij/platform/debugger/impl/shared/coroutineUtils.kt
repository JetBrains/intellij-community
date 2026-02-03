// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus

// Used only for Java code, since MutableStateFlow function cannot be called there.
@ApiStatus.Internal
fun <T> createMutableStateFlow(initialValue: T): MutableStateFlow<T> {
  return MutableStateFlow(initialValue)
}

@ApiStatus.Internal
fun <T, R> mapFlow(f: Flow<T>, mapper: (T) -> R): Flow<R> = f.map(mapper)

// Used only for Java code, since MutableSharedFlow function cannot be called there.
@ApiStatus.Internal
fun <T> createMutableSharedFlow(replay: Int, extraBufferCapacity: Int): MutableSharedFlow<T> {
  return MutableSharedFlow(replay, extraBufferCapacity)
}