// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import kotlin.coroutines.cancellation.CancellationException

/**
 * A marker interface for exceptions that should never be logged.
 */
interface ControlFlowException

/**
 * Whether this exception is a _control flow exception_ that should never be logged.
 *
 * _Control flow exceptions_ are:
 * - [CancellationException] (including [ProcessCanceledException][com.intellij.openapi.progress.ProcessCanceledException])
 * - [ControlFlowException]
 */
val Throwable.isControlFlowException: Boolean
  get() = this is ControlFlowException || this is CancellationException