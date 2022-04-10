// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.introspect.VisibilityChecker
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.collaboration.api.HttpClientUtil
import com.intellij.collaboration.api.dto.GraphQLErrorDTO
import com.intellij.collaboration.api.dto.GraphQLRequestDTO
import com.intellij.collaboration.api.dto.GraphQLResponseDTO
import com.intellij.collaboration.api.graphql.GraphQLErrorException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGQLQueryLoader
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.text.SimpleDateFormat
import java.util.*

/**
 * A base for a collection of API methods
 */
abstract class GitLabApiRequestsBase {

  suspend fun GitLabApi.gqlQuery(server: GitLabServerPath, queryPath: String, input: Any? = null): HttpRequest {
    val bodyPublisher = withContext(Dispatchers.IO) {
      val query = GitLabGQLQueryLoader.loadQuery(queryPath)
      HttpRequest.BodyPublishers.ofString(gqlJackson.writeValueAsString(GraphQLRequestDTO(query, input)))
    }
    return request(server)
      .POST(bodyPublisher)
      .header(HttpClientUtil.CONTENT_TYPE_HEADER, HttpClientUtil.CONTENT_TYPE_JSON)
      .build()
  }

  suspend inline fun <reified T> GitLabApi.loadRestJson(request: HttpRequest): T {
    return withContext(Dispatchers.IO) {
      val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).await()
      HttpClientUtil.handleResponse(response) {
        restJackson.readValue(it, T::class.java)!!
      }
    }
  }

  suspend inline fun <reified T> GitLabApi.loadGQLResponse(request: HttpRequest, vararg pathFromData: String): T? {
    val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).await()
    return withContext(Dispatchers.IO) {
      val responseType = gqlJackson.typeFactory
        .constructParametricType(GraphQLResponseDTO::class.java, JsonNode::class.java, GraphQLErrorDTO::class.java)
      HttpClientUtil.handleResponse(response) { stream ->
        extractObject(gqlJackson.readValue(stream, responseType), *pathFromData)
      }
    }
  }

  @PublishedApi
  internal inline fun <reified T> extractObject(result: GraphQLResponseDTO<out JsonNode, GraphQLErrorDTO>,
                                                vararg pathFromData: String): T? {
    val data = result.data
    if (data != null && !data.isNull) {
      var node: JsonNode = data
      for (path in pathFromData) {
        node = node[path] ?: break
      }
      if (!node.isNull) return gqlJackson.readValue(node.toString(), T::class.java)
    }
    val errors = result.errors
    if (errors == null) return null
    else throw GraphQLErrorException(errors)
  }

  companion object {

    @PublishedApi
    internal val restJackson = jacksonObjectMapper().genericConfig()
      .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    @PublishedApi
    internal val gqlJackson: ObjectMapper = jacksonObjectMapper().genericConfig()
      .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)

    private fun ObjectMapper.genericConfig(): ObjectMapper =
      this.setDateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"))
        .setTimeZone(TimeZone.getDefault())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .setVisibility(VisibilityChecker.Std(JsonAutoDetect.Visibility.NONE,
                                             JsonAutoDetect.Visibility.NONE,
                                             JsonAutoDetect.Visibility.NONE,
                                             JsonAutoDetect.Visibility.NONE,
                                             JsonAutoDetect.Visibility.ANY))
  }
}