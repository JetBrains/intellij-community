// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Kotlin-friendly aliases of `ExceptionUtilRt` methods.
 */

package com.intellij.util

import org.jetbrains.annotations.ApiStatus

/**
 * Alias to [ExceptionUtilRt.causeChain].
 */
@ApiStatus.Experimental
fun Throwable.causeChain(): Sequence<Throwable> {
  return ExceptionUtilRt.causeChain(this).asSequence()
}