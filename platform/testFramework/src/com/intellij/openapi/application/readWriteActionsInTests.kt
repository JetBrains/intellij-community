// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.openapi.util.ThrowableComputable

/**
 * Executes [action] inside write action.
 * If called from outside the EDT, transfers control to the EDT first, executes write action there and waits for the execution end.
 */
inline fun <T> runWriteActionAndWait(crossinline action: () -> T): T {
  return WriteAction.computeAndWait(ThrowableComputable { action() })
}
