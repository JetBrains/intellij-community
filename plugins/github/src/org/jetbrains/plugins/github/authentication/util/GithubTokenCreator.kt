// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.util

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubAuthorization
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import java.io.IOException

/**
 * Handy helper for creating OAuth token
 */
class GithubTokenCreator(private val server: GithubServerPath,
                         private val executor: GithubApiRequestExecutor,
                         private val indicator: ProgressIndicator) {
  @Throws(IOException::class)
  fun createMaster(@Nls(capitalization = Nls.Capitalization.Title) noteSuffix: String): GithubAuthorization {
    return safeCreate(MASTER_SCOPES, ApplicationNamesInfo.getInstance().fullProductName + " " + noteSuffix + " access token")
  }

  @Throws(IOException::class)
  private fun safeCreate(scopes: List<String>, note: String): GithubAuthorization {
    try {
      return executor.execute(indicator, GithubApiRequests.Auth.create(server, scopes, note))
    }
    catch (e: GithubStatusCodeException) {
      if (e.error != null && e.error!!.containsErrorCode("already_exists")) {
        // with new API we can't reuse old token, so let's just create new one
        // we need to change note as well, because it should be unique

        //TODO: handle better
        val current = executor.execute(indicator, GithubApiRequests.Auth.get(server))
        for (i in 1..99) {
          val newNote = note + "_" + i
          if (current.find { authorization -> newNote == authorization.note } == null) {
            return executor.execute(indicator, GithubApiRequests.Auth.create(server, scopes, newNote))
          }
        }
      }
      throw e
    }
  }

  companion object {
    private val MASTER_SCOPES = listOf("repo", "gist")
    const val DEFAULT_CLIENT_NAME = "Github Integration Plugin"
  }
}