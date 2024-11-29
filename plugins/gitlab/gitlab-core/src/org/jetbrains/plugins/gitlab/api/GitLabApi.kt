// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.graphql.GraphQLApiHelper
import com.intellij.collaboration.api.httpclient.*
import com.intellij.collaboration.api.json.JsonHttpApiHelper
import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.api.json.loadOptionalJsonList
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.HttpSecurityUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.GitLabServersManager
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApiStatus.Experimental
sealed interface GitLabApi : HttpApiHelper {
  val server: GitLabServerPath

  val graphQL: GraphQL
  val rest: Rest

  /**
   * Gets metadata from server or from cache.
   *
   * @throws java.net.ConnectException when there is no usable internet connection.
   * @throws com.intellij.collaboration.api.HttpStatusErrorException when the API request results
   * in a non-successful status code.
   */
  suspend fun getMetadata(): GitLabServerMetadata

  interface GraphQL : GraphQLApiHelper, GitLabApi
  interface Rest : JsonHttpApiHelper, GitLabApi
}

// this dark inheritance magic is required to make extensions work properly
internal class GitLabApiImpl(
  private val serversManager: GitLabServersManager,
  override val server: GitLabServerPath,
  httpHelper: HttpApiHelper
) : GitLabApi, HttpApiHelper by httpHelper {
  constructor(
    serversManager: GitLabServersManager,
    server: GitLabServerPath,
    tokenSupplier: (() -> String)? = null
  ) : this(serversManager, server, tokenSupplier?.let { httpHelper(it) } ?: httpHelper())

  override suspend fun getMetadata(): GitLabServerMetadata =
    serversManager.getMetadata(this)

  override val graphQL: GitLabApi.GraphQL =
    GraphQLImpl(GraphQLApiHelper(logger<GitLabApi>(),
                                 this,
                                 GitLabGQLDataDeSerializer,
                                 GitLabGQLDataDeSerializer))

  private inner class GraphQLImpl(helper: GraphQLApiHelper) :
    GitLabApi by this,
    GitLabApi.GraphQL,
    GraphQLApiHelper by helper

  override val rest: GitLabApi.Rest =
    RestImpl(JsonHttpApiHelper(logger<GitLabApi>(),
                               this,
                               GitLabRestJsonDataDeSerializer,
                               GitLabRestJsonDataDeSerializer))

  private inner class RestImpl(helper: JsonHttpApiHelper) :
    GitLabApi by this,
    GitLabApi.Rest,
    JsonHttpApiHelper by helper
}

suspend fun GitLabApi.getMetadataOrNull(): GitLabServerMetadata? =
  runCatchingUser { getMetadata() }.getOrNull()

suspend fun GitLabApi.GraphQL.gitLabQuery(query: GitLabGQLQuery, variablesObject: Any? = null): HttpRequest {
  if (query == GitLabGQLQuery.GET_METADATA) {
    return query(server.gqlApiUri, { GitLabGQLQueryLoaders.default.loadQuery(query.filePath) }, variablesObject)
  }

  val serverMeta = getMetadata()
  val queryLoader = GitLabGQLQueryLoaders.forMetadata(serverMeta)

  return query(server.gqlApiUri, { queryLoader.loadQuery(query.filePath) }, variablesObject)
}

suspend inline fun <reified T> GitLabApi.Rest.loadList(requestName: GitLabApiRequestName, uri: String)
  : HttpResponse<out List<T>> {
  val request = request(uri).GET().build()
  return withErrorStats(requestName) {
    loadJsonList(request)
  }
}

suspend inline fun <reified T> GitLabApi.Rest.loadUpdatableJsonList(requestName: GitLabApiRequestName, uri: URI,
                                                                    eTag: String? = null)
  : HttpResponse<out List<T>?> {
  val request = request(uri).GET().apply {
    if (eTag != null) {
      header("If-None-Match", eTag)
    }
  }.build()
  return withErrorStats(requestName) {
    loadOptionalJsonList(request)
  }
}

@Throws(GitLabGraphQLMutationException::class)
fun <R : Any, MR : GitLabGraphQLMutationResultDTO<R>> HttpResponse<out MR?>.getResultOrThrow(): R {
  val result = body()
  if (result == null) throw GitLabGraphQLMutationEmptyResultException()
  val errors = result.errors
  if (!errors.isNullOrEmpty()) throw GitLabGraphQLMutationErrorException(errors)
  return result.value as R
}


private fun httpHelper(tokenSupplier: () -> String): HttpApiHelper {
  val authConfigurer = object : AuthorizationConfigurer() {
    override val authorizationHeaderValue: String
      get() = HttpSecurityUtil.createBearerAuthHeaderValue(tokenSupplier())
  }
  val requestConfigurer = CompoundRequestConfigurer(RequestTimeoutConfigurer(), GitLabHeadersConfigurer(), authConfigurer)
  return HttpApiHelper(logger = logger<GitLabApi>(),
                       requestConfigurer = requestConfigurer)
}

private fun httpHelper(): HttpApiHelper {
  val requestConfigurer = CompoundRequestConfigurer(RequestTimeoutConfigurer(), GitLabHeadersConfigurer())
  return HttpApiHelper(logger = logger<GitLabApi>(),
                       requestConfigurer = requestConfigurer)
}

private const val PLUGIN_USER_AGENT_NAME = "IntelliJ-GitLab-Plugin"

private class GitLabHeadersConfigurer : HttpRequestConfigurer {
  override fun configure(builder: HttpRequest.Builder): HttpRequest.Builder =
    builder.apply {
      header(HttpClientUtil.ACCEPT_ENCODING_HEADER, HttpClientUtil.CONTENT_ENCODING_GZIP)
      header(HttpClientUtil.USER_AGENT_HEADER, HttpClientUtil.getUserAgentValue(PLUGIN_USER_AGENT_NAME))
    }
}