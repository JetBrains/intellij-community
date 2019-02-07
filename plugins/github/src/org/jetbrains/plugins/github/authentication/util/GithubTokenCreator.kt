// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.util

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubAuthorization
import org.jetbrains.plugins.github.api.requests.GithubRequestPagination
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import java.io.IOException
import java.util.*

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
        val newNote = createUniqueNote(note)
        return executor.execute(indicator, GithubApiRequests.Auth.create(server, scopes, newNote))
      }
      throw e
    }
  }

  private fun createUniqueNote(note: String): String {
    val existingNotes = GithubApiPagesLoader
      .loadAll(executor, indicator, GithubApiRequests.Auth.pages(server, GithubRequestPagination()))
      .mapNotNull { it.note }

    val index = findNextDeduplicationIndex(note, existingNotes)
    return if (index == 0) note else note + "_$index"
  }

  companion object {
    private val MASTER_SCOPES = listOf("repo", "gist")
    const val DEFAULT_CLIENT_NAME = "Github Integration Plugin"

    @JvmStatic
    internal fun findNextDeduplicationIndex(note: String, existingNotes: List<String>): Int {

      val existingIndices = TreeSet<Int>()
      //extract numerical index if possible and put it into sorted set
      for (existingNote in existingNotes) {
        if (!existingNote.startsWith(note, true)) continue

        val indexPart = existingNote.substring(note.length)
        val index: Int
        if (indexPart.isEmpty()) index = 0
        else if (!indexPart.startsWith('_')) continue
        else index = indexPart.substring(1).toIntOrNull() ?: continue

        existingIndices.add(index)
      }

      if (existingIndices.isEmpty()) return 0

      var lastIndex = -1
      for (index in existingIndices) {
        if (index - lastIndex > 1) return lastIndex + 1
        else lastIndex = index
      }
      return existingIndices.last() + 1
    }
  }
}