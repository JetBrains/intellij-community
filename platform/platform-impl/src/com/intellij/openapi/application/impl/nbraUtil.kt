// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.readLockCompensationTimeout
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.intellij.IntellijCoroutines
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier
import kotlin.time.Duration.Companion.milliseconds

@OptIn(InternalCoroutinesApi::class)
@ApiStatus.Internal
internal fun <T> runSynchronousNonBlockingReadActionWithCompensation(r: Supplier<T>) : T {
  return IntellijCoroutines.runAndCompensateParallelism(readLockCompensationTimeout.milliseconds * 2) {
    r.get()
  }
}