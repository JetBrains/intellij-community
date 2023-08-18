// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import kotlinx.coroutines.CompletableJob
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

@ApiStatus.Internal
class CancellationFunction<T, U> internal constructor(
  private val job: CompletableJob,
  private val function: Function<T, U>,
) : Function<T, U> {

  override fun apply(t: T): U {
    return runAsCoroutine(job, completeOnFinish = true) {
      function.apply(t)
    }
  }

  override fun toString(): String {
    return function.toString()
  }
}
