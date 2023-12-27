// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines

import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Similar to [kotlinx.coroutines.flow.FlowCollector] but can be used concurrently.
 *
 * @see kotlinx.coroutines.flow.FlowCollector
 */
@Experimental
sealed interface TransformCollector<R> {

  /**
   * Send a value to the output of the current transformation.
   * This method is thread-safe and can be invoked concurrently.
   */
  suspend fun out(value: R)

  suspend operator fun invoke(value: R): Unit = out(value)
}
