// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.diagnostic.isControlFlowException
import com.intellij.util.ExceptionUtilRt

/**
 * Rethrows the given exception [e] if it's a _control flow exception_ (see [isControlFlowException]).
 *
 * The current stack trace is added to the rethrown exception as a suppressed exception.
 *
 * If [e] is null, then this function is a no-op.
 */
fun rethrowControlFlowException(e: Throwable?) {
  if (e != null && e.isControlFlowException) {
    throw ExceptionUtilRt.addRethrownStackAsSuppressed(e)
  }
}
