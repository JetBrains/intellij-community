// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.readLockCompensationTimeout
import com.intellij.util.IntelliJCoroutinesFacade
import java.util.function.Supplier
import kotlin.time.Duration.Companion.milliseconds

internal fun <T> runSynchronousNonBlockingReadActionWithCompensation(r: Supplier<T>) : T {
  return IntelliJCoroutinesFacade.runAndCompensateParallelism(readLockCompensationTimeout.milliseconds * 2) {
    r.get()
  }
}