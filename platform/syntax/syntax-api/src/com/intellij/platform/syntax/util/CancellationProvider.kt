// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util

import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cancellation Provider allows stopping a task if it is cancelled by external factors.
 *
 * @See com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory.Builder.withCancellationProvider
 */
@ApiStatus.Experimental
fun interface CancellationProvider {

  @Throws(CancellationException::class)
  fun checkCancelled()
}