// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.graphql.GraphQLApiHelper
import com.intellij.collaboration.api.httpclient.CompoundRequestConfigurer
import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.httpclient.HttpRequestConfigurer
import com.intellij.collaboration.api.httpclient.RequestTimeoutConfigurer
import com.intellij.collaboration.api.json.JsonHttpApiHelper
import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.api.json.loadOptionalJsonList
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.HttpSecurityUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.GitLabServersManager
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private val LOG: Logger = logger<GitLabApi>()

sealed interface GitLabApi : GitLabApiHelper, HttpApiHelper {
  val graphQL: GraphQL
  val rest: Rest

  interface GraphQL : GraphQLApiHelper, GitLabApiHelper
  interface Rest : JsonHttpApiHelper, GitLabApiHelper
}

interface GitLabApiHelper : HttpApiHelper {
  val server: GitLabServerPath

  /**
   * Gets metadata from server or from cache.
   *
   * @throws java.net.ConnectException when there is no usable internet connection.
   * @throws com.intellij.collaboration.api.HttpStatusErrorException when the API request results
   * in a non-successful status code.
   */
  suspend fun getMetadata(): GitLabServerMetadata
}

// this dark inheritance magic is required to make extensions work properly
internal class GitLabApiImpl(
  private val serversManager: GitLabServersManager,
  override val server: GitLabServerPath,
  httpHelper: HttpApiHelper,
) : GitLabApi, HttpApiHelper by httpHelper {
  constructor(
    serversManager: GitLabServersManager,
    server: GitLabServerPath,
    tokenSupplier: (suspend () -> String)? = null,
  ) : this(serversManager, server, tokenSupplier?.let { httpHelper(server, it) } ?: httpHelper())

  override suspend fun getMetadata(): GitLabServerMetadata =
    serversManager.getMetadata(this)

  override val graphQL: GitLabApi.GraphQL =
    GraphQLImpl(GraphQLApiHelper(LOG,
                                 this,
                                 GitLabGQLDataDeSerializer,
                                 GitLabGQLDataDeSerializer))

  private inner class GraphQLImpl(helper: GraphQLApiHelper) :
    GitLabApiHelper by this,
    GitLabApi.GraphQL,
    GraphQLApiHelper by helper

  override val rest: GitLabApi.Rest =
    RestImpl(JsonHttpApiHelper(LOG,
                               this,
                               GitLabRestJsonDataDeSerializer,
                               GitLabRestJsonDataDeSerializer))

  private inner class RestImpl(helper: JsonHttpApiHelper) :
    GitLabApiHelper by this,
    GitLabApi.Rest,
    JsonHttpApiHelper by helper
}

//region REST
context(api: GitLabApi.Rest)
suspend inline fun <reified T : Any> HttpRequest.loadValue(requestName: GitLabApiRequestName): T =
  api.withErrorStats(requestName) {
    loadJsonValue<T>().body()
  }

context(api: GitLabApi.Rest)
suspend inline fun <reified T : Any> HttpRequest.loadList(requestName: GitLabApiRequestName): List<T> =
  api.withErrorStats(requestName) {
    loadJsonList<T>().body()
  }

/**
 * Performs a conditional `GET`, sending [eTag] (when present) as an `If-None-Match` header.
 *
 * A `304 Not Modified` is not an error: it is surfaced as a normal response with a `null` body (and the response
 * status/headers preserved), so the caller can serve its cached value and keep following pagination links.
 */
suspend inline fun <reified T> GitLabApi.Rest.getJsonListConditional(
  requestName: GitLabApiRequestName, uri: URI,
  eTag: String? = null,
): HttpResponse<out List<T>?> {
  val request = request(uri).GET().apply {
    if (eTag != null) {
      header(HttpClientUtil.IF_NONE_MATCH_HEADER, eTag)
    }
  }.build()
  return withErrorStats(requestName) {
    request.loadOptionalJsonList()
  }
}
//endregion

//region GraphQL
suspend fun GitLabApi.GraphQL.gitLabQuery(query: GitLabGQLQuery, variablesObject: Any? = null): HttpRequest {
  if (query == GitLabGQLQuery.GET_METADATA) {
    return query(server.gqlApiUri, { GitLabGQLQueryLoaders.default.loadQuery(query.filePath) }, variablesObject)
  }

  val serverMeta = getMetadata()
  val queryLoader = GitLabGQLQueryLoaders.forMetadata(serverMeta)

  return query(server.gqlApiUri, { queryLoader.loadQuery(query.filePath) }, variablesObject)
}

context(api: GitLabApi.GraphQL)
suspend inline fun <reified T : Any> HttpRequest.loadResponse(query: GitLabGQLQuery, vararg pathFromData: String): T? {
  val request = this
  return api.withErrorStats(query) {
    api.loadResponseByClass(request, T::class.java, *pathFromData).body()
  }
}

suspend inline fun <reified T> GitLabApi.GraphQL.runQuery(
  query: GitLabGQLQuery,
  vararg pathFromData: String,
): T? = gitLabQuery(query).loadResponse(query, *pathFromData)

suspend inline fun <reified T> GitLabApi.GraphQL.runQuery(
  query: GitLabGQLQuery,
  variablesMap: Map<String, Any?>,
  vararg pathFromData: String,
): T? = gitLabQuery(query, variablesMap).loadResponse(query, *pathFromData)

@Throws(GitLabGraphQLMutationException::class)
fun <R : Any, MR : GitLabGraphQLMutationResultDTO<R>> MR?.getResultOrThrow(): R {
  val result = this
  if (result == null) throw GitLabGraphQLMutationEmptyResultException()
  val errors = result.errors
  if (!errors.isNullOrEmpty()) throw GitLabGraphQLMutationErrorException(errors)
  return result.value as R
}
//endregion

//region Infra
suspend fun GitLabApiHelper.getMetadataOrNull(): GitLabServerMetadata? =
  runCatchingUser { getMetadata() }.getOrNull()

private fun httpHelper(server: GitLabServerPath, tokenSupplier: suspend () -> String): HttpApiHelper {
  val authConfigurer = object : HttpRequestConfigurer {
    override suspend fun configureSuspend(builder: HttpRequest.Builder): HttpRequest.Builder {
      val uri = builder.build().uri()
      if (server.isAuthorizedUrl(uri)) {
        val token = tokenSupplier()
        val headerValue = HttpSecurityUtil.createBearerAuthHeaderValue(token)
        return builder.header(HttpSecurityUtil.AUTHORIZATION_HEADER_NAME, headerValue)
      }
      else {
        return builder
      }
    }
  }
  val requestConfigurer = CompoundRequestConfigurer(RequestTimeoutConfigurer(), GitLabHeadersConfigurer(), authConfigurer)
  return HttpApiHelper(logger = LOG,
                       requestConfigurer = requestConfigurer)
}

private fun GitLabServerPath.isAuthorizedUrl(targetUri: URI): Boolean {
  val serverUri = toURI()

  if (targetUri.host != serverUri.host) {
    LOG.info("URL $targetUri host does not match the server $serverUri. Authorization will not be granted")
    return false
  }
  if (targetUri.port != serverUri.port) {
    LOG.info("URL $targetUri port does not match the server $serverUri. Authorization will not be granted")
    return false
  }
  if (targetUri.scheme != null && targetUri.scheme != serverUri.scheme) {
    LOG.info("URL $targetUri protocol does not match the server $serverUri. Authorization will not be granted")
    return false
  }
  if (serverUri.scheme == "http") {
    LOG.warn("URL $targetUri use HTTP, not HTTPS, token leak is possible")
  }
  return true
}

private fun httpHelper(): HttpApiHelper {
  val requestConfigurer = CompoundRequestConfigurer(RequestTimeoutConfigurer(), GitLabHeadersConfigurer())
  return HttpApiHelper(logger = LOG,
                       requestConfigurer = requestConfigurer)
}

private const val PLUGIN_USER_AGENT_NAME = "IntelliJ-GitLab-Plugin"

private class GitLabHeadersConfigurer : HttpRequestConfigurer {
  override suspend fun configureSuspend(builder: HttpRequest.Builder): HttpRequest.Builder =
    builder.apply {
      header(HttpClientUtil.ACCEPT_ENCODING_HEADER, HttpClientUtil.CONTENT_ENCODING_GZIP)
      header(HttpClientUtil.USER_AGENT_HEADER, HttpClientUtil.getUserAgentValue(PLUGIN_USER_AGENT_NAME))
    }
}
//endregion