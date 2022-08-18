// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.graphql.CachingGraphQLQueryLoader
import com.intellij.collaboration.api.graphql.GraphQLApiClient
import com.intellij.collaboration.api.graphql.GraphQLDataSerializer
import com.intellij.collaboration.api.httpclient.HttpClientFactory
import com.intellij.collaboration.api.httpclient.HttpRequestConfigurer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger

class GitLabApi(override val clientFactory: HttpClientFactory,
                override val requestConfigurer: HttpRequestConfigurer) : GraphQLApiClient() {

  @Suppress("SSBasedInspection")
  override val logger: Logger = LOG

  override val gqlQueryLoader: CachingGraphQLQueryLoader = GitLabGQLQueryLoader

  override val gqlSerializer: GraphQLDataSerializer = GitLabGQLDataSerializer

  companion object {
    private val LOG = logger<GitLabApi>()
  }
}