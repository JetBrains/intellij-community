// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.CliResult
import com.intellij.ide.ProtocolHandler
import com.intellij.openapi.progress.ProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture

internal class JBProtocolHandler : ProtocolHandler {
  override fun getScheme() = JBProtocolCommand.SCHEME

  override fun process(query: String, indicator: ProgressIndicator): CompletableFuture<CliResult> {
    return ApplicationManager.getApplication().coroutineScope.async {
      val commandResult = withContext(Dispatchers.EDT) {
        JBProtocolCommand.execute(query)
      }

      withContext(Dispatchers.IO) {
        CliResult(0, commandResult.get())
      }
    }.asCompletableFuture()
  }
}
