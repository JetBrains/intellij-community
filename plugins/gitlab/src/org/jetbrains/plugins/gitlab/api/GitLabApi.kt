// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.graphql.GraphQLApiHelper
import com.intellij.collaboration.api.httpclient.*
import com.intellij.collaboration.api.json.JsonHttpApiHelper
import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.api.json.loadOptionalJsonList
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.HttpSecurityUtil
import org.jetbrains.plugins.gitlab.GitLabServersManager
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
import org.jetbrains.plugins.gitlab.api.request.getServerMetadataOrVersion
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

sealed interface GitLabApi : HttpApiHelper {
  val graphQL: GraphQL
  val rest: Rest

  interface GraphQL : GraphQLApiHelper, GitLabApi
  interface Rest : JsonHttpApiHelper, GitLabApi
}

// this dark inheritance magic is required to make extensions work properly
class GitLabApiImpl(httpHelper: HttpApiHelper) : GitLabApi, HttpApiHelper by httpHelper {

  constructor(tokenSupplier: () -> String) : this(httpHelper(tokenSupplier))

  constructor() : this(httpHelper())

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

suspend fun GitLabApi.GraphQL.gitLabQuery(serverPath: GitLabServerPath, query: GitLabGQLQuery, variablesObject: Any? = null): HttpRequest {
  val serverMeta = service<GitLabServersManager>().getMetadata(serverPath) {
    runCatching { rest.getServerMetadataOrVersion(it) }
  }
  val queryLoader = if (serverMeta?.enterprise == false) {
    GitLabGQLQueryLoaders.community
  }
  else {
    GitLabGQLQueryLoaders.default
  }
  return query(serverPath.gqlApiUri, { queryLoader.loadQuery(query.filePath) }, variablesObject)
}

suspend inline fun <reified T> GitLabApi.Rest.loadList(serverPath: GitLabServerPath, requestName: GitLabApiRequestName, uri: String)
  : HttpResponse<out List<T>> {
  val request = request(uri).GET().build()
  return withErrorStats(serverPath, requestName) {
    loadJsonList(request)
  }
}

suspend inline fun <reified T> GitLabApi.Rest.loadUpdatableJsonList(serverPath: GitLabServerPath, requestName: GitLabApiRequestName,
                                                                    uri: URI, eTag: String? = null)
  : HttpResponse<out List<T>?> {
  val request = request(uri).GET().apply {
    if (eTag != null) {
      header("If-None-Match", eTag)
    }
  }.build()
  return withErrorStats(serverPath, requestName) {
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
  val requestConfigurer = CompoundRequestConfigurer(RequestTimeoutConfigurer(), GitLabHeadersConfigurer, authConfigurer)
  return HttpApiHelper(logger = logger<GitLabApi>(),
                       requestConfigurer = requestConfigurer)
}

private fun httpHelper(): HttpApiHelper {
  val requestConfigurer = CompoundRequestConfigurer(RequestTimeoutConfigurer(), GitLabHeadersConfigurer)
  return HttpApiHelper(logger = logger<GitLabApi>(),
                       requestConfigurer = requestConfigurer)
}

private const val PLUGIN_USER_AGENT_NAME = "IntelliJ-GitLab-Plugin"

private object GitLabHeadersConfigurer : HttpRequestConfigurer {
  override fun configure(builder: HttpRequest.Builder): HttpRequest.Builder =
    builder.apply {
      header(HttpClientUtil.ACCEPT_ENCODING_HEADER, HttpClientUtil.CONTENT_ENCODING_GZIP)
      header(HttpClientUtil.USER_AGENT_HEADER, HttpClientUtil.getUserAgentValue(PLUGIN_USER_AGENT_NAME))
    }
}