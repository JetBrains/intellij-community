// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import kotlinx.coroutines.CompletableJob
import java.util.function.BiConsumer

internal class CancellationBiConsumer<T, U>(private val myJob: CompletableJob, private val myRunnable: BiConsumer<T, U>) : BiConsumer<T, U> {
  override fun accept(t: T, u: U) {
    runAsCoroutine(myJob) { myRunnable.accept(t, u) }
  }
}
