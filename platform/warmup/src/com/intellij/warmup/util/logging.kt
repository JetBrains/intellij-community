// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.openapi.progress.asContextElement
import com.intellij.openapi.progress.impl.ProgressState
import com.intellij.openapi.progress.impl.TextDetailsProgressReporter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlin.time.Duration.Companion.milliseconds

/**
 * Installs a progress reporter that sends the information about progress to the stdout instead of UI.
 */
suspend fun <T> withLoggingProgressReporter(action: suspend CoroutineScope.() -> T): T = coroutineScope {
  TextDetailsProgressReporter(this).use { reporter ->
    val reportToCommandLineJob = reportToStdout(reporter.progressState)
    try {
      withContext(reporter.asContextElement(), action)
    }
    finally {
      reportToCommandLineJob.cancel()
    }
  }
}

@OptIn(FlowPreview::class)
private fun CoroutineScope.reportToStdout(
  stateFlow: Flow<ProgressState>
): Job {
  return launch(Dispatchers.IO) {
    stateFlow.sample(300.milliseconds).distinctUntilChanged().collect {
      progressStateText(it)?.let(ConsoleLog::info)
    }
  }
}