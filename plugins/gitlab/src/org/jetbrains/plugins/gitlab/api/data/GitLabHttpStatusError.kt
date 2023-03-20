// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.data

import com.intellij.collaboration.api.HttpStatusErrorException
import org.jetbrains.plugins.gitlab.api.GitLabRestJsonDataDeSerializer
import java.io.StringReader

class GitLabHttpStatusError(val error: String) {
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
  return GitLabRestJsonDataDeSerializer.fromJson(StringReader(actualBody), GitLabHttpStatusError::class.java)
}