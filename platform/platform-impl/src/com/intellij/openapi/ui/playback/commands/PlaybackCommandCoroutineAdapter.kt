// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.playback.PlaybackCommand
import com.intellij.openapi.ui.playback.PlaybackContext
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

abstract class PlaybackCommandCoroutineAdapter(protected val text: @NonNls String, protected val line: Int) : PlaybackCommand {
  companion object {
    const val CMD_PREFIX: @NonNls String = "%"
  }

  protected abstract suspend fun doExecute(context: PlaybackContext)

  fun extractCommandArgument(prefix: String): String {
    return if (text.startsWith(prefix)) text.substring(prefix.length).trim { it <= ' ' } else text
  }

  override fun canGoFurther(): Boolean = true

  override fun execute(context: PlaybackContext): Promise<Any> {
    context.code(text, line)

    val promise = AsyncPromise<Any>()
    ApplicationManager.getApplication().coroutineScope.launch {
      doExecute(context)
    }.invokeOnCompletion {
      if (it == null) {
        promise.setResult(null)
      }
      else {
        context.error(text, line)
        promise.setError(it)
      }
    }
    return promise
  }
}