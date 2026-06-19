// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import java.io.IOException
import java.nio.file.NoSuchFileException

internal fun isCodexExecutableNotFound(error: Throwable): Boolean {
  return error.anyCause { cause ->
    when (cause) {
      is NoSuchFileException -> true
      is IOException -> isExecutableNotFoundMessage(cause.message)
      else -> false
    }
  }
}

internal fun Throwable.isCodexThreadReadIncludeTurnsFallback(): Boolean {
  return anyCauseMessage { message ->
    message.contains("includeTurns is unavailable before first user message") ||
    message.contains("ephemeral threads do not support includeTurns")
  }
}

private inline fun Throwable.anyCause(predicate: (Throwable) -> Boolean): Boolean {
  return generateSequence(this) { it.cause }.any(predicate)
}

private inline fun Throwable.anyCauseMessage(predicate: (String) -> Boolean): Boolean {
  return anyCause { cause -> cause.message?.let(predicate) == true }
}

private fun isExecutableNotFoundMessage(message: String?): Boolean {
  return message != null &&
         (message.contains("error=2") ||
          message.contains("no such file or directory", ignoreCase = true) ||
          message.contains("cannot find the file", ignoreCase = true))
}
