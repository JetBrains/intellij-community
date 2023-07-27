// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.graphql.GraphQLErrorException
import com.intellij.collaboration.api.json.HttpJsonDeserializationException
import com.intellij.openapi.components.service
import org.jetbrains.plugins.gitlab.GitLabServersManager
import org.jetbrains.plugins.gitlab.api.request.getServerMetadataOrVersion
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

suspend fun <T> GitLabApi.GraphQL.withErrorStats(server: GitLabServerPath,
                                                 query: GitLabGQLQuery,
                                                 responseClass: Class<*>,
                                                 loader: suspend () -> T): T {
  try {
    return loader()
  }
  catch (e: GraphQLErrorException) {
    if (e.errors.any { it.message.contains("doesn't exist on type") }) {
      val version = tryGetServerVersion(server)
      GitLabStatistics.logGqlModelError(query, version)
    }
    throw e
  }
  catch (e: HttpStatusErrorException) {
    if (e.statusCode in 500..599) {
      val version = tryGetServerVersion(server)
      GitLabStatistics.logServerError(GitLabApiRequestName.of(query), server.isDefault, version)
    }
    throw e
  }
  catch (e: HttpJsonDeserializationException) {
    val version = tryGetServerVersion(server)
    GitLabStatistics.logJsonDeserializationError(responseClass, version)
    throw e
  }
}

suspend inline fun <reified T> GitLabApi.GraphQL.withErrorStats(server: GitLabServerPath,
                                                                query: GitLabGQLQuery,
                                                                noinline loader: suspend () -> T): T {
  return withErrorStats(server, query, T::class.java, loader)
}

suspend fun <T> GitLabApi.Rest.withErrorStats(server: GitLabServerPath,
                                              requestName: GitLabApiRequestName,
                                              responseClass: Class<*>,
                                              loader: suspend () -> T): T {
  try {
    return loader()
  }
  catch (e: HttpStatusErrorException) {
    if (e.statusCode in 500..599) {
      val version = tryGetServerVersion(server)
      GitLabStatistics.logServerError(requestName, server.isDefault, version)
    }
    throw e
  }
  catch (e: HttpJsonDeserializationException) {
    val version = tryGetServerVersion(server)
    GitLabStatistics.logJsonDeserializationError(responseClass, version)
    throw e
  }
}

suspend inline fun <reified T> GitLabApi.Rest.withErrorStats(server: GitLabServerPath,
                                                             requestName: GitLabApiRequestName,
                                                             noinline loader: suspend () -> T): T {
  return withErrorStats(server, requestName, T::class.java, loader)
}

private suspend fun GitLabApi.tryGetServerVersion(server: GitLabServerPath): String? =
  service<GitLabServersManager>().getMetadata(server) {
    runCatching { rest.getServerMetadataOrVersion(it) }
  }?.version