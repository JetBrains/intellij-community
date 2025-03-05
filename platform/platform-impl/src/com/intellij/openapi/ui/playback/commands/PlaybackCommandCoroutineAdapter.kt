// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback.commands

import com.intellij.openapi.ui.playback.PlaybackCommand
import com.intellij.openapi.ui.playback.PlaybackContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.NonNls
import java.util.concurrent.CompletableFuture

abstract class PlaybackCommandCoroutineAdapter(protected val text: @NonNls String, protected val line: Int) : PlaybackCommand {
  companion object {
    const val CMD_PREFIX: @NonNls String = "%"
  }

  protected abstract suspend fun doExecute(context: PlaybackContext)

  fun extractCommandArgument(prefix: String): String {
    return if (text.startsWith(prefix)) text.substring(prefix.length).trim() else text
  }

  override fun canGoFurther(): Boolean = true

  @OptIn(DelicateCoroutinesApi::class)
  final override fun execute(context: PlaybackContext): CompletableFuture<*> {
    context.code(text, line)

    // ExitAppCommand cannot use app scope
    val job = GlobalScope.async {
      doExecute(context)
    }
    job.invokeOnCompletion {
      if (it != null) {
        context.error(text + ": " + it.message + "\nException: ${it.stackTraceToString()}", line)
      }
    }
    return job.asCompletableFuture()
  }
}