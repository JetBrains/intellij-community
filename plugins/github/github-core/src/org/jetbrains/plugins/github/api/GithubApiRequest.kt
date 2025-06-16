// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.collaboration.api.dto.GraphQLRequestDTO
import com.intellij.collaboration.api.dto.GraphQLResponseDTO
import com.intellij.collaboration.api.util.LinkHttpHeaderValue
import com.intellij.util.ThrowableConvertor
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.api.data.GithubResponsePage
import org.jetbrains.plugins.github.api.data.GithubSearchResult
import org.jetbrains.plugins.github.api.data.graphql.GHGQLError
import org.jetbrains.plugins.github.api.data.graphql.GHGQLRateLimit
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubConfusingException
import org.jetbrains.plugins.github.exceptions.GithubJsonException
import java.io.IOException

/**
 * Represents an API request with strictly defined response type
 */
sealed class GithubApiRequest<out T>(val url: String) {
  var operation: GithubApiRequestOperation = GithubApiRequestOperation.Unknown
  var operationName: String? = null
    private set

  abstract val acceptMimeType: String?

  protected val headers = mutableMapOf<String, String>()
  val additionalHeaders: Map<String, String>
    get() = headers

  @Throws(IOException::class)
  abstract fun extractResult(response: GithubApiResponse): T

  fun withOperation(operation: GithubApiRequestOperation): GithubApiRequest<T> {
    this.operation = operation
    return this
  }

  fun withOperationName(name: String): GithubApiRequest<T> {
    operationName = name
    return this
  }

  abstract class Get<T> @JvmOverloads constructor(
    url: String,
    override val acceptMimeType: String? = null,
  ) : GithubApiRequest<T>(url) {
    abstract class Optional<T> @JvmOverloads constructor(
      url: String,
      acceptMimeType: String? = null,
    ) : Get<T?>(url, acceptMimeType) {
      companion object {
        inline fun <reified T> json(url: String, acceptMimeType: String? = null): Optional<T> =
          Json(url, T::class.java, acceptMimeType)
      }

      open class Json<T>(url: String, private val clazz: Class<T>, acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE)
        : Optional<T>(url, acceptMimeType) {

        override fun extractResult(response: GithubApiResponse): T = parseJsonObject(response, clazz)
      }
    }

    companion object {
      inline fun <reified T> json(url: String, acceptMimeType: String? = null): Get<T> =
        Json(url, T::class.java, acceptMimeType)

      inline fun <reified T> jsonPage(url: String, acceptMimeType: String? = null): Get<GithubResponsePage<T>> =
        JsonPage(url, T::class.java, acceptMimeType)

      inline fun <reified T> jsonSearchPage(url: String, acceptMimeType: String? = null): Get<GithubResponsePage<T>> =
        JsonSearchPage(url, T::class.java, acceptMimeType)
    }

    open class Json<T>(url: String, private val clazz: Class<T>, acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE)
      : Get<T>(url, acceptMimeType) {

      override fun extractResult(response: GithubApiResponse): T = parseJsonObject(response, clazz)
    }

    open class JsonMap<T, U>(url: String, acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE)
      : Get<Map<T, U>>(url, acceptMimeType) {

      override fun extractResult(response: GithubApiResponse): Map<T, U> = parseJsonMap(response)
    }

    open class JsonPage<T>(url: String, private val clazz: Class<T>, acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE)
      : Get<GithubResponsePage<T>>(url, acceptMimeType) {

      override fun extractResult(response: GithubApiResponse): GithubResponsePage<T> {
        val list = parseJsonList(response, clazz)
        val linkHeader = response.findHeader(LinkHttpHeaderValue.HEADER_NAME)?.let(LinkHttpHeaderValue::parse)
        return GithubResponsePage(list, linkHeader)
      }
    }

    open class JsonSearchPage<T>(
      url: String,
      private val clazz: Class<T>,
      acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE,
    ) : Get<GithubResponsePage<T>>(url, acceptMimeType) {

      override fun extractResult(response: GithubApiResponse): GithubResponsePage<T> {
        val page = parseJsonSearchPage(response, clazz)
        val linkHeader = response.findHeader(LinkHttpHeaderValue.HEADER_NAME)?.let(LinkHttpHeaderValue::parse)
        return GithubResponsePage(page.items, linkHeader)
      }
    }
  }

  abstract class Head<T> @JvmOverloads constructor(
    url: String,
    override val acceptMimeType: String? = null,
  ) : GithubApiRequest<T>(url)

  abstract class WithBody<out T>(url: String) : GithubApiRequest<T>(url) {
    abstract val body: String?
    abstract val bodyMimeType: String
  }

  abstract class Post<out T> @JvmOverloads constructor(
    override val bodyMimeType: String,
    url: String,
    override var acceptMimeType: String? = null,
  ) : WithBody<T>(url) {
    companion object {
      inline fun <reified T> json(url: String, body: Any, acceptMimeType: String? = null): Post<T> =
        Json(url, body, T::class.java, acceptMimeType)
    }

    open class Json<T>(
      url: String, private val bodyObject: Any, private val clazz: Class<T>,
      acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE,
    ) : Post<T>(GithubApiContentHelper.JSON_MIME_TYPE, url, acceptMimeType) {

      override val body: String
        get() = GithubApiContentHelper.toJson(bodyObject)

      override fun extractResult(response: GithubApiResponse): T = parseJsonObject(response, clazz)
    }

    abstract class GQLQuery<out T>(
      url: String,
      @VisibleForTesting
      internal val queryName: String,
      @VisibleForTesting
      internal val variablesObject: Map<String, Any?>,
    ) : Post<T>(GithubApiContentHelper.JSON_MIME_TYPE, url) {
      @get:VisibleForTesting
      internal abstract val clazz: Class<*>

      override val body: String
        get() {
          val query = GHGQLQueryLoader.loadQuery(queryName)
          val request = GraphQLRequestDTO(query, variablesObject)
          return GithubApiContentHelper.toJson(request, true)
        }

      protected fun throwException(errors: List<GHGQLError>): Nothing {
        if (errors.any { it.type.equals("INSUFFICIENT_SCOPES", true) })
          throw GithubAuthenticationException("Access token has not been granted the required scopes.")

        if (errors.size == 1) throw GithubConfusingException(errors.single().toString())
        throw GithubConfusingException(errors.toString())
      }

      abstract fun extractResultWithCost(response: GithubApiResponse): Pair<T, GHGQLRateLimit?>

      override fun extractResult(response: GithubApiResponse): T =
        extractResultWithCost(response).first

      class Parsed<out T>(
        url: String,
        requestFilePath: String,
        variablesObject: Map<String, Any?>,
        override val clazz: Class<out T>,
      ) : GQLQuery<T>(url, requestFilePath, variablesObject) {
        override fun extractResultWithCost(response: GithubApiResponse): Pair<T, GHGQLRateLimit?> {
          return parseResponse(response, clazz)
                 ?: throw GithubJsonException("Non-nullable entity is null or entity path is invalid")
        }
      }

      abstract class Traversed<out T>(
        url: String,
        requestFilePath: String,
        variablesObject: Map<String, Any?>,
        @VisibleForTesting
        internal val pathFromData: Array<out String>
      ) : GQLQuery<T>(url, requestFilePath, variablesObject)

      class TraversedParsed<out T : Any>(
        url: String,
        requestFilePath: String,
        variablesObject: Map<String, Any?>,
        override val clazz: Class<out T>,
        vararg pathFromData: String,
      ) : Traversed<T>(url, requestFilePath, variablesObject, pathFromData) {
        override fun extractResultWithCost(response: GithubApiResponse): Pair<T, GHGQLRateLimit?> {
          return parseResponse(response, clazz, pathFromData)
                 ?: throw GithubJsonException("Non-nullable entity is null or entity path is invalid")
        }
      }

      class OptionalTraversedParsed<T>(
        url: String,
        requestFilePath: String,
        variablesObject: Map<String, Any?>,
        override val clazz: Class<out T>,
        vararg pathFromData: String,
      ) : Traversed<T?>(url, requestFilePath, variablesObject, pathFromData) {
        override fun extractResultWithCost(response: GithubApiResponse): Pair<T?, GHGQLRateLimit?> {
          return parseResponse(response, clazz, pathFromData) ?: (null to null)
        }
      }

      class OptionalTraversedParsedList<T>(
        url: String,
        requestFilePath: String,
        variablesObject: Map<String, Any?>,
        override val clazz: Class<out T>,
        vararg pathFromData: String,
      ) : Traversed<List<T>?>(url, requestFilePath, variablesObject, pathFromData) {
        override fun extractResultWithCost(response: GithubApiResponse): Pair<List<T>?, GHGQLRateLimit?> =
          parseResponse(response, pathFromData) {
            GithubApiContentHelper.readJsonList(it.toString().reader(), clazz)
          } ?: (null to null)
      }

      protected fun <T> parseResponse(
        response: GithubApiResponse,
        clazz: Class<T>,
      ): Pair<T, GHGQLRateLimit?>? =
        parseResponse(response, arrayOf()) {
          GithubApiContentHelper.fromJson(it.toString(), clazz, true)
        }

      protected fun <T> parseResponse(
        response: GithubApiResponse,
        clazz: Class<T>,
        pathFromData: Array<out String>,
      ): Pair<T, GHGQLRateLimit?>? =
        parseResponse(response, pathFromData) {
          GithubApiContentHelper.fromJson(it.toString(), clazz, true)
        }

      protected fun <T> parseResponse(
        response: GithubApiResponse,
        pathFromData: Array<out String>,
        deserialize: (JsonNode) -> T,
      ): Pair<T, GHGQLRateLimit?>? {
        val result = parseGQLResponse(response, JsonNode::class.java)
        val data = result.data
        if (data != null && !data.isNull) {
          var node: JsonNode = data
          for (path in pathFromData) {
            node = node[path] ?: break
          }
          if (!node.isNull) {
            val result = deserialize(node)
            val rates = data["rateLimit"]
              ?.takeIf { !it.isNull }
              ?.let { GithubApiContentHelper.fromJson<GHGQLRateLimit?>(it.toString(), gqlNaming = true) }
            return result to rates
          }
        }
        val errors = result.errors
        if (errors == null) return null
        else throwException(errors)
      }
    }
  }

  abstract class Put<T> @JvmOverloads constructor(
    override val bodyMimeType: String,
    url: String,
    override val acceptMimeType: String? = null,
  ) : WithBody<T>(url) {
    companion object {
      inline fun <reified T> json(url: String, body: Any? = null): Put<T> = Json(url, body, T::class.java)

      inline fun <reified T> jsonList(url: String, body: Any): Put<List<T>> = JsonList(url, body, T::class.java)
    }

    open class Json<T>(url: String, private val bodyObject: Any?, private val clazz: Class<T>)
      : Put<T>(GithubApiContentHelper.JSON_MIME_TYPE, url, GithubApiContentHelper.V3_JSON_MIME_TYPE) {
      init {
        if (bodyObject == null) headers["Content-Length"] = "0"
      }

      override val body: String?
        get() = bodyObject?.let { GithubApiContentHelper.toJson(it) }

      override fun extractResult(response: GithubApiResponse): T = parseJsonObject(response, clazz)
    }

    open class JsonList<T>(url: String, private val bodyObject: Any?, private val clazz: Class<T>)
      : Put<List<T>>(GithubApiContentHelper.JSON_MIME_TYPE, url, GithubApiContentHelper.V3_JSON_MIME_TYPE) {
      init {
        if (bodyObject == null) headers["Content-Length"] = "0"
      }

      override val body: String?
        get() = bodyObject?.let { GithubApiContentHelper.toJson(it) }

      override fun extractResult(response: GithubApiResponse): List<T> = parseJsonList(response, clazz)
    }
  }

  abstract class Patch<T> @JvmOverloads constructor(
    override val bodyMimeType: String,
    url: String,
    override var acceptMimeType: String? = null,
  ) : Post<T>(bodyMimeType,
              url,
              acceptMimeType) {
    companion object {
      inline fun <reified T> json(url: String, body: Any): Post<T> = Json(url, body, T::class.java)
    }

    // TODO: Mark as PATCH, currently this likely sends POSTs?
    open class Json<T>(url: String, bodyObject: Any, clazz: Class<T>) : Post.Json<T>(url, bodyObject, clazz)
  }

  abstract class Delete<T> @JvmOverloads constructor(
    override val bodyMimeType: String,
    url: String,
    override val acceptMimeType: String? = null,
  ) : WithBody<T>(url) {

    companion object {
      inline fun <reified T> json(url: String, body: Any? = null): Delete<T> = Json(url, body, T::class.java)
    }

    open class Json<T>(url: String, private val bodyObject: Any? = null, private val clazz: Class<T>)
      : Delete<T>(GithubApiContentHelper.JSON_MIME_TYPE, url, GithubApiContentHelper.V3_JSON_MIME_TYPE) {
      init {
        if (bodyObject == null) headers["Content-Length"] = "0"
      }

      override val body: String?
        get() = bodyObject?.let { GithubApiContentHelper.toJson(it) }

      override fun extractResult(response: GithubApiResponse): T = parseJsonObject(response, clazz)
    }
  }

  companion object {
    private fun <T> parseJsonObject(response: GithubApiResponse, clazz: Class<T>): T {
      return response.readBody(ThrowableConvertor { GithubApiContentHelper.readJsonObject(it, clazz) })
    }

    private fun <T> parseJsonList(response: GithubApiResponse, clazz: Class<T>): List<T> {
      return response.readBody(ThrowableConvertor { GithubApiContentHelper.readJsonList(it, clazz) })
    }

    private fun <T, U> parseJsonMap(response: GithubApiResponse): Map<T, U> {
      return response.readBody(ThrowableConvertor { GithubApiContentHelper.readJsonMap(it) })
    }

    private fun <T> parseJsonSearchPage(response: GithubApiResponse, clazz: Class<T>): GithubSearchResult<T> {
      return response.readBody(ThrowableConvertor {
        @Suppress("UNCHECKED_CAST")
        GithubApiContentHelper.readJsonObject(it, GithubSearchResult::class.java, clazz) as GithubSearchResult<T>
      })
    }

    private fun <T> parseGQLResponse(response: GithubApiResponse, dataClass: Class<out T>): GraphQLResponseDTO<out T, GHGQLError> {
      return response.readBody(ThrowableConvertor {
        @Suppress("UNCHECKED_CAST")
        GithubApiContentHelper.readJsonObject(it, GraphQLResponseDTO::class.java, dataClass, GHGQLError::class.java,
                                              gqlNaming = true) as GraphQLResponseDTO<T, GHGQLError>
      })
    }
  }
}