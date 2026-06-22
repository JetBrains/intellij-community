// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.opencode.sessions.server

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

private object OpenCodeServerShutdownLogCategory

private val LOG = logger<OpenCodeServerShutdownLogCategory>()

@OptIn(AwaitCancellationAndInvoke::class)
internal fun registerOpenCodeShutdownOnCancellation(scope: CoroutineScope, onShutdown: suspend CoroutineScope.() -> Unit) {
  val job = scope.coroutineContext[Job]
  if (job == null) {
    LOG.warn("OpenCode server scope has no Job; shutdown hook not installed")
    return
  }

  scope.awaitCancellationAndInvoke(CoroutineName("OpenCode server shutdown") + Dispatchers.IO) {
    LOG.debug { "OpenCode server scope cancelling; shutting down client" }
    onShutdown()
  }
}
