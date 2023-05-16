// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.graphql.GraphQLApiHelper
import com.intellij.collaboration.api.httpclient.AuthorizationConfigurer
import com.intellij.collaboration.api.httpclient.CommonHeadersConfigurer
import com.intellij.collaboration.api.httpclient.CompoundRequestConfigurer
import com.intellij.collaboration.api.httpclient.RequestTimeoutConfigurer
import com.intellij.collaboration.api.json.JsonHttpApiHelper
import com.intellij.collaboration.api.json.loadJsonList
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.HttpSecurityUtil
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
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
      val requestConfigurer = CompoundRequestConfigurer(RequestTimeoutConfigurer(),
                                                        CommonHeadersConfigurer(),
                                                        authConfigurer)
      return HttpApiHelper(logger = logger<GitLabApi>(),
                           requestConfigurer = requestConfigurer)
    }

    private fun httpHelper(): HttpApiHelper {
      val requestConfigurer = CompoundRequestConfigurer(RequestTimeoutConfigurer(),
                                                        CommonHeadersConfigurer())
      return HttpApiHelper(logger = logger<GitLabApi>(),
                           requestConfigurer = requestConfigurer)
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