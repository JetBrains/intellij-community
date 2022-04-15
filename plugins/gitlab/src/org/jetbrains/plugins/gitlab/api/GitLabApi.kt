// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.introspect.VisibilityChecker
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.collaboration.api.dto.GraphQLErrorDTO
import com.intellij.collaboration.api.dto.GraphQLRequestDTO
import com.intellij.collaboration.api.dto.GraphQLResponseDTO
import com.intellij.collaboration.api.graphql.CachingGraphQLQueryLoader
import com.intellij.collaboration.api.graphql.GraphQLErrorException
import com.intellij.collaboration.api.httpclient.*
import com.intellij.openapi.diagnostic.logger
import java.awt.Image
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler
import java.text.SimpleDateFormat
import java.util.*

class GitLabApi(private val clientFactory: HttpClientFactory,
                private val requestConfigurer: HttpRequestConfigurer) {

  val client: HttpClient
    get() = clientFactory.createClient()

  fun request(uri: String): HttpRequest.Builder =
    request(URI.create(uri))

  fun request(uri: URI): HttpRequest.Builder =
    HttpRequest.newBuilder(uri).apply(requestConfigurer::configure)

  fun gqlQuery(server: GitLabServerPath, queryPath: String, input: Any? = null): HttpRequest =
    request(server.gqlApiUri)
      .POST(GitLabGraphQLQueryBodyPublisher(server.gqlApiUri, queryPath, input))
      .header(HttpClientUtil.CONTENT_TYPE_HEADER, HttpClientUtil.CONTENT_TYPE_JSON)
      .build()


  suspend inline fun <reified T> loadGQLResponse(request: HttpRequest, vararg pathFromData: String): HttpResponse<T?> {
    return client.sendAndAwaitCancellable(request, gqlBodyHandler(request, pathFromData, T::class.java))
  }

  fun <T> gqlBodyHandler(request: HttpRequest, pathFromData: Array<out String>, clazz: Class<T>): BodyHandler<T?> =
    GitLabGraphQLResponseBodyHandler(request, pathFromData, clazz)

  suspend inline fun loadImage(request: HttpRequest): HttpResponse<Image> {
    return client.sendAndAwaitCancellable(request, imageBodyHandler(request))
  }

  fun imageBodyHandler(request: HttpRequest): BodyHandler<Image> =
    object : ImageBodyHandler(request) {
      override fun handleError(statusCode: Int, errorBody: String): Nothing {
        LOG.debug("${request.logName()} : Error ${statusCode}")
        if (LOG.isTraceEnabled) {
          LOG.trace("${request.logName()} : Response body: $errorBody")
        }
        super.handleError(statusCode, errorBody)
      }
    }


  companion object {

    private val LOG = logger<GitLabApi>()

    private val gqlJackson: ObjectMapper = jacksonObjectMapper()
      .genericConfig()
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



    private class GitLabGraphQLQueryBodyPublisher(private val uri: URI, queryPath: String, input: Any? = null)
      : GraphQLQueryBodyPublisher(queryPath, input) {

      override val queryLoader: CachingGraphQLQueryLoader = GitLabGQLQueryLoader

      override fun serialize(request: GraphQLRequestDTO): ByteArray {
        LOG.debug("Request POST $uri")
        if (LOG.isTraceEnabled) {
          LOG.trace("Request POST $uri body: " + gqlJackson.writeValueAsString(request))
        }
        return gqlJackson.writeValueAsBytes(request)
      }
    }



    class GitLabGraphQLResponseBodyHandler<T>(request: HttpRequest,
                                              private val pathFromData: Array<out String>,
                                              private val clazz: Class<T>)
      : StreamReadingBodyHandler<T?>(request) {

      override fun read(bodyStream: InputStream): T? {
        LOG.debug("${request.logName()} : Success")
        val responseType = gqlJackson.typeFactory
          .constructParametricType(GraphQLResponseDTO::class.java, JsonNode::class.java, GraphQLErrorDTO::class.java)

        val gqlResponse: GraphQLResponseDTO<out JsonNode, GraphQLErrorDTO> = if (LOG.isTraceEnabled) {
          val body = bodyStream.reader().readText()
          LOG.trace("${request.logName()} : Response body: $body")
          gqlJackson.readValue(body, responseType)
        }
        else {
          gqlJackson.readValue(bodyStream, responseType)
        }
        return extractData(gqlResponse)
      }

      private fun extractData(result: GraphQLResponseDTO<out JsonNode, GraphQLErrorDTO>): T? {
        val data = result.data
        if (data != null && !data.isNull) {
          var node: JsonNode = data
          for (path in pathFromData) {
            node = node[path] ?: break
          }
          if (!node.isNull) return gqlJackson.readValue(node.toString(), clazz)
        }
        val errors = result.errors
        if (errors == null) return null
        else throw GraphQLErrorException(errors)
      }

      override fun handleError(statusCode: Int, errorBody: String): Nothing {
        LOG.debug("${request.logName()} : Error ${statusCode}")
        if (LOG.isTraceEnabled) {
          LOG.trace("${request.logName()} : Response body: $errorBody")
        }
        super.handleError(statusCode, errorBody)
      }
    }

    private fun HttpRequest.logName(): String = "Request ${method()} ${uri()}"
  }
}