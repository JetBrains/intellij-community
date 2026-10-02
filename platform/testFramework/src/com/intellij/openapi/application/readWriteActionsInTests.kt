// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.util.ThrowableComputable

/**
 * Executes [action] inside write action.
 * If called from outside the EDT, transfers control to the EDT first, executes write action there and waits for the execution end.
 *
 * Relies on the [WriteThread] API, which as of now runs its write actions on the EDT.
 * In the future versions of the IntelliJ Platform this can change.
 */
inline fun <T> runWriteActionAndWait(crossinline action: () -> T): T {
  return WriteAction.computeAndWait(ThrowableComputable { action() })
}
