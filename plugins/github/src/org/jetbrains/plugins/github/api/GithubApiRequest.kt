// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.fasterxml.jackson.databind.JavaType
import com.intellij.util.ThrowableConvertor
import org.jetbrains.plugins.github.api.data.GithubResponsePage
import org.jetbrains.plugins.github.api.data.GithubSearchResult
import java.io.IOException

/**
 * Represents an API request with strictly defined response type
 */
sealed class GithubApiRequest<T>(val url: String) {
  var operationName: String? = null
  abstract val acceptMimeType: String?

  protected val headers = mutableMapOf<String, String>()
  val additionalHeaders: Map<String, String>
    get() = headers

  @Throws(IOException::class)
  abstract fun extractResult(response: GithubApiResponse): T

  fun withOperationName(name: String): GithubApiRequest<T> {
    operationName = name
    return this
  }

  abstract class Get<T> @JvmOverloads constructor(url: String,
                                                  override val acceptMimeType: String? = null) : GithubApiRequest<T>(url) {
    abstract class Optional<T> @JvmOverloads constructor(url: String,
                                                         acceptMimeType: String? = null) : Get<T?>(url, acceptMimeType) {
      companion object {
        inline fun <reified T> json(url: String, acceptMimeType: String? = null): Optional<T> =
          Json(url, T::class.java, acceptMimeType)
      }

      open class Json<T>(url: String, clazz: Class<T>, acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE)
        : Optional<T>(url, acceptMimeType) {

        private val type = constructType(clazz)

        override fun extractResult(response: GithubApiResponse): T = parseJsonResponse(response, type)
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

    open class Json<T>(url: String, clazz: Class<T>, acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE)
      : Get<T>(url, acceptMimeType) {

      private val type = constructType(clazz)

      override fun extractResult(response: GithubApiResponse): T = parseJsonResponse(response, type)
    }

    open class JsonList<T>(url: String, clazz: Class<T>, acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE)
      : Get<List<T>>(url, acceptMimeType) {

      private val type = constructListType(clazz)

      override fun extractResult(response: GithubApiResponse): List<T> = parseJsonResponse(response, type)
    }

    open class JsonPage<T>(url: String, clazz: Class<T>, acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE)
      : Get<GithubResponsePage<T>>(url, acceptMimeType) {

      private val type = constructListType(clazz)

      override fun extractResult(response: GithubApiResponse): GithubResponsePage<T> {
        return GithubResponsePage.parseFromHeader(parseJsonResponse(response, type),
                                                  response.findHeader(GithubResponsePage.HEADER_NAME))
      }
    }

    open class JsonSearchPage<T>(url: String, clazz: Class<T>, acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE)
      : Get<GithubResponsePage<T>>(url, acceptMimeType) {

      private val type = constructSearchResultType(clazz)

      override fun extractResult(response: GithubApiResponse): GithubResponsePage<T> {
        return GithubResponsePage.parseFromHeader(parseJsonResponse<GithubSearchResult<T>>(response, type).items,
                                                  response.findHeader(GithubResponsePage.HEADER_NAME))
      }
    }
  }

  abstract class Head<T> @JvmOverloads constructor(url: String,
                                                   override val acceptMimeType: String? = null) : GithubApiRequest<T>(url)

  abstract class WithBody<T>(url: String) : GithubApiRequest<T>(url) {
    abstract val body: String?
    abstract val bodyMimeType: String
  }

  abstract class Post<T> @JvmOverloads constructor(override val bodyMimeType: String,
                                                   url: String,
                                                   override val acceptMimeType: String? = null) : GithubApiRequest.WithBody<T>(url) {
    companion object {
      inline fun <reified T> json(url: String, body: Any): Post<T> = Json(url, body, T::class.java)
    }

    open class Json<T>(url: String, private val bodyObject: Any, clazz: Class<T>) : Post<T>(GithubApiContentHelper.JSON_MIME_TYPE,
                                                                                            url,
                                                                                            GithubApiContentHelper.V3_JSON_MIME_TYPE) {
      override val body: String
        get() = GithubApiContentHelper.toJson(bodyObject)

      private val type = constructType(clazz)

      override fun extractResult(response: GithubApiResponse): T = parseJsonResponse(response, type)
    }
  }


  abstract class Put<T> @JvmOverloads constructor(override val bodyMimeType: String,
                                                  url: String,
                                                  override val acceptMimeType: String? = null) : GithubApiRequest.WithBody<T>(url) {
    companion object {
      inline fun <reified T> json(url: String, body: Any? = null): Put<T> = Json(url, body, T::class.java)

      inline fun <reified T> jsonList(url: String, body: Any): Put<List<T>> = JsonList(url, body, T::class.java)
    }

    open class Json<T>(url: String, private val bodyObject: Any?, clazz: Class<T>) : Put<T>(GithubApiContentHelper.JSON_MIME_TYPE,
                                                                                            url,
                                                                                            GithubApiContentHelper.V3_JSON_MIME_TYPE) {
      init {
        if (bodyObject == null) headers["Content-Length"] = "0"
      }

      override val body: String?
        get() = bodyObject?.let { GithubApiContentHelper.toJson(it) }

      private val type = constructType(clazz)

      override fun extractResult(response: GithubApiResponse): T = parseJsonResponse(response, type)
    }

    open class JsonList<T>(url: String, private val bodyObject: Any?, clazz: Class<T>) : Put<List<T>>(GithubApiContentHelper.JSON_MIME_TYPE,
                                                                                                      url,
                                                                                                      GithubApiContentHelper.V3_JSON_MIME_TYPE) {
      init {
        if (bodyObject == null) headers["Content-Length"] = "0"
      }

      override val body: String?
        get() = bodyObject?.let { GithubApiContentHelper.toJson(it) }

      private val type = constructListType(clazz)

      override fun extractResult(response: GithubApiResponse): List<T> = parseJsonResponse(response, type)
    }
  }

  abstract class Patch<T> @JvmOverloads constructor(override val bodyMimeType: String,
                                                    url: String,
                                                    override val acceptMimeType: String? = null) : Post<T>(bodyMimeType,
                                                                                                           url,
                                                                                                           acceptMimeType) {
    companion object {
      inline fun <reified T> json(url: String, body: Any): Post<T> = Json(url, body, T::class.java)
    }

    open class Json<T>(url: String, bodyObject: Any, clazz: Class<T>) : Post.Json<T>(url, bodyObject, clazz)
  }

  abstract class Delete<T> @JvmOverloads constructor(override val bodyMimeType: String,
                                                     url: String,
                                                     override val acceptMimeType: String? = null) : GithubApiRequest.WithBody<T>(url) {

    companion object {
      inline fun <reified T> json(url: String, body: Any? = null): Delete<T> = Json(url, body, T::class.java)
    }

    open class Json<T>(url: String, private val bodyObject: Any? = null, clazz: Class<T>) : Delete<T>(GithubApiContentHelper.JSON_MIME_TYPE,
                                                                                                      url,
                                                                                                      GithubApiContentHelper.V3_JSON_MIME_TYPE) {
      init {
        if (bodyObject == null) headers["Content-Length"] = "0"
      }

      override val body: String?
        get() = bodyObject?.let { GithubApiContentHelper.toJson(it) }

      private val type = constructType(clazz)

      override fun extractResult(response: GithubApiResponse): T = parseJsonResponse(response, type)
    }
  }

  companion object {
    private fun constructSearchResultType(clazz: Class<*>): JavaType = GithubApiContentHelper.jackson.typeFactory
      .constructParametricType(GithubSearchResult::class.java, clazz)

    private fun constructListType(clazz: Class<*>): JavaType = GithubApiContentHelper.jackson.typeFactory
      .constructCollectionType(List::class.java, clazz)

    private fun constructType(clazz: Class<*>): JavaType {
      return GithubApiContentHelper.jackson.typeFactory.constructSimpleType(clazz, emptyArray())
    }

    private fun <T> parseJsonResponse(response: GithubApiResponse, type: JavaType): T {
      return response.readBody(ThrowableConvertor { GithubApiContentHelper.readJson<T>(it, type) })
    }
  }
}
