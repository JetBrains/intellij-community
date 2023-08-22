// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import java.util.function.BiConsumer
import kotlin.coroutines.Continuation

internal class CancellationBiConsumer<T, U>(
  private val continuation: Continuation<Unit>,
  private val runnable: BiConsumer<T, U>,
) : BiConsumer<T, U> {
  override fun accept(t: T, u: U) {
    runAsCoroutine(continuation, completeOnFinish = true) {
      runnable.accept(t, u)
    }
  }
}
