// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ide.CliResult
import com.intellij.ide.ProtocolHandler
import com.intellij.openapi.progress.ProgressIndicator
import java.util.concurrent.CompletableFuture

class JBProtocolHandler : ProtocolHandler {
  override fun getScheme(): String = JBProtocolCommand.SCHEME

  override fun process(query: String, indicator: ProgressIndicator): CompletableFuture<CliResult> {
    val result = CompletableFuture<CliResult>()
    ApplicationManager.getApplication().invokeLater(
      {
        val commandResult = runCatching { JBProtocolCommand.execute(query) }
        ProcessIOExecutorService.INSTANCE.execute {
          commandResult.mapCatching { it.get() }
            .onFailure { result.completeExceptionally(it) }
            .onSuccess { result.complete(CliResult(0, it)) }
        }
      },
      ModalityState.NON_MODAL)
    return result
  }
}
