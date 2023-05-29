// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.graphql.GraphQLApiHelper
import com.intellij.collaboration.api.httpclient.*
import com.intellij.collaboration.api.json.JsonHttpApiHelper
import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.HttpSecurityUtil
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
import java.net.http.HttpRequest
import java.net.http.HttpResponse

interface GitLabApi : HttpApiHelper, JsonHttpApiHelper, GraphQLApiHelper

class GitLabApiImpl private constructor(httpHelper: HttpApiHelper)
  : GitLabApi,
    HttpApiHelper by httpHelper,
    JsonHttpApiHelper by JsonHttpApiHelper(logger<GitLabApi>(),
                                           httpHelper,
                                           GitLabRestJsonDataDeSerializer,
                                           GitLabRestJsonDataDeSerializer),
    GraphQLApiHelper by GraphQLApiHelper(logger<GitLabApi>(),
                                         httpHelper,
                                         GitLabGQLQueryLoader,
                                         GitLabGQLDataDeSerializer,
                                         GitLabGQLDataDeSerializer) {

  constructor(tokenSupplier: () -> String) : this(httpHelper(tokenSupplier))

  constructor() : this(httpHelper())

  companion object {
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
  }
}

suspend inline fun <reified T> GitLabApi.loadList(uri: String): HttpResponse<out List<T>> {
  val request = request(uri).GET().build()
  return loadJsonList(request)
}

@Throws(GitLabGraphQLMutationException::class)
fun <R : Any, MR : GitLabGraphQLMutationResultDTO<R>> HttpResponse<out MR?>.getResultOrThrow(): R {
  val result = body()
  if (result == null) throw GitLabGraphQLMutationEmptyResultException()
  val errors = result.errors
  if (!errors.isNullOrEmpty()) throw GitLabGraphQLMutationErrorException(errors)
  return result.value as R
}