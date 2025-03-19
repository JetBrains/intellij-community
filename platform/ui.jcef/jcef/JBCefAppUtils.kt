// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun JBCefApp.getRemoteDebugPortSuspendable(): Int? {
  return suspendCancellableCoroutine { continuation ->
    this.getRemoteDebuggingPort { port ->
      continuation.resume(port)
    }
  }
}
