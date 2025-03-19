// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.api.graphql.loadResponse
import com.intellij.collaboration.api.httpclient.HttpClientUtil.inflateAndReadWithErrorHandlingAndLogging
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import org.jetbrains.plugins.gitlab.api.*
import org.jetbrains.plugins.gitlab.api.GitLabEdition.Community
import org.jetbrains.plugins.gitlab.api.GitLabEdition.Enterprise
import org.jetbrains.plugins.gitlab.api.dto.GitLabServerMetadataDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabServerVersionDTO
import org.jsoup.Jsoup
import java.net.http.HttpResponse

private val LOG = logger<GitLabApi>()

/**
 * Checks whether the given server looks like and acts like a GitLab server.
 *
 * Note that this depends on the /projects API endpoint being unauthenticated.
 * It has been this since at least 9.0 and it is still. If GitLab changes the
 * visibility of projects, this function will need to be replaced.
 *
 * Maybe we can combine behavioural checks in the future if this turns out to
 * be insufficient to recognize GitLab server.
 */
@SinceGitLab("9.0", note = "Older, but no need to figure out exactly")
suspend fun GitLabApi.Rest.checkIsGitLabServer(): Boolean {
  val uri = server.restApiUri.resolveRelative("projects?page=1&per_page=1")
  val request = request(uri).GET().build()
  return try {
    // Skip error reporting done in JsonHttpApiHelper
    val bodyHandler = inflateAndReadWithErrorHandlingAndLogging(LOG, request) { reader, _ ->
       GitLabRestJsonDataDeSerializer.fromJson(reader, List::class.java)
    }
    sendAndAwaitCancellable(request, bodyHandler).body() != null
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Exception) {
    false
  }
}

// Unauthenticated
@SinceGitLab("9.0")
suspend fun GitLabApi.Rest.guessServerEdition(): GitLabEdition? {
  val uri = server.toURI().resolveRelative("help")
  val request = request(uri).GET().build()
  val bodyHandler = inflateAndReadWithErrorHandlingAndLogging(LOG, request) { reader, _ ->
    // Parses the /help page and attempts to find and parse the header containing edition info
    val titleText = Jsoup.parse(reader.readText()).select("h1").text()
    if (!titleText.contains("GitLab", true)) return@inflateAndReadWithErrorHandlingAndLogging null
    if (titleText.contains("Enterprise", true)) Enterprise else Community
  }
  return sendAndAwaitCancellable(request, bodyHandler).body()
}

// Authenticated
// should not have statistics to avoid recursion
@SinceGitLab("15.6")
suspend fun GitLabApi.GraphQL.getServerMetadata(): HttpResponse<out GitLabServerMetadataDTO?> {
  val request = gitLabQuery(GitLabGQLQuery.GET_METADATA)
  return loadResponse(request, "metadata")
}

// Authenticated
@SinceGitLab("8.13", deprecatedIn = "15.5")
suspend fun GitLabApi.Rest.getServerVersion(): HttpResponse<out GitLabServerVersionDTO> {
  val uri = server.restApiUri.resolveRelative("version")
  val request = request(uri).GET().build()
  return loadJsonValue(request)
}