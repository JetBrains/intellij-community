// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.common

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun CoroutineScope.invokeOnCompletionOrWarn(
  log: Logger,
  missingJobMessage: String,
  action: () -> Unit,
) {
  val job = coroutineContext[Job]
  if (job == null) {
    log.warn(missingJobMessage)
    return
  }
  job.invokeOnCompletion { action() }
}
