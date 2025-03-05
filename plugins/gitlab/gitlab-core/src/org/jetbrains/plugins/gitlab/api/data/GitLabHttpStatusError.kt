// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.data

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.GitLabRestJsonDataDeSerializer
import java.io.StringReader

data class GitLabHttpStatusError(val error: @NlsSafe String) {
  val statusErrorType: HttpStatusErrorType = parseStatusError(error)

  enum class HttpStatusErrorType {
    INVALID_TOKEN,
    UNKNOWN
  }

  companion object {
    private fun parseStatusError(errorText: String): HttpStatusErrorType = try {
      HttpStatusErrorType.valueOf(errorText.uppercase())
    }
    catch (_: Throwable) {
      HttpStatusErrorType.UNKNOWN
    }
  }
}

fun HttpStatusErrorException.asGitLabStatusError(): GitLabHttpStatusError? {
  val actualBody = body ?: return null
  return try {
    StringReader(actualBody).use {
      GitLabRestJsonDataDeSerializer.fromJson(it, GitLabHttpStatusError::class.java)
    }
  }
  catch (e: Exception) {
    null
  }
}