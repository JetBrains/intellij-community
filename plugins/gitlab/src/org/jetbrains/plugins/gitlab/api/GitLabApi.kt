// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.graphql.GraphQLApiHelper
import com.intellij.collaboration.api.httpclient.CommonHeadersConfigurer
import com.intellij.collaboration.api.httpclient.CompoundRequestConfigurer
import com.intellij.collaboration.api.httpclient.CredentialsAuthorizationConfigurer
import com.intellij.collaboration.api.httpclient.RequestTimeoutConfigurer
import com.intellij.collaboration.api.json.JsonHttpApiHelper
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.HttpSecurityUtil

class GitLabApi private constructor(httpHelper: HttpApiHelper)
  : HttpApiHelper by httpHelper,
    JsonHttpApiHelper by JsonHttpApiHelper(logger<GitLabApi>(),
                                           httpHelper,
                                           GitLabRestJsonDataDeSerializer,
                                           GitLabRestJsonDataDeSerializer),
    GraphQLApiHelper by GraphQLApiHelper(logger<GitLabApi>(),
                                         httpHelper,
                                         GitLabGQLQueryLoader,
                                         GitLabGQLDataDeSerializer,
                                         GitLabGQLDataDeSerializer) {

  constructor(token: String) : this(httpHelper(token))

  companion object {
    private fun httpHelper(token: String): HttpApiHelper {
      val authConfigurer = CredentialsAuthorizationConfigurer(token,
                                                              HttpSecurityUtil::createBearerAuthHeaderValue)
      val requestConfigurer = CompoundRequestConfigurer(RequestTimeoutConfigurer(),
                                                        CommonHeadersConfigurer(),
                                                        authConfigurer)
      return HttpApiHelper(logger = logger<GitLabApi>(),
                           requestConfigurer = requestConfigurer)
    }

  }
}