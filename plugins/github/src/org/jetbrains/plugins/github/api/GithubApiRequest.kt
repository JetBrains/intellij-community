// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.google.gson.reflect.TypeToken
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

        private val typeToken = TypeToken.get(clazz)

        override fun extractResult(response: GithubApiResponse): T = parseJsonResponse(response, typeToken)
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

      private val typeToken = TypeToken.get(clazz)

      override fun extractResult(response: GithubApiResponse): T = parseJsonResponse(response, typeToken)
    }

    open class JsonList<T>(url: String, clazz: Class<T>, acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE)
      : Get<List<T>>(url, acceptMimeType) {

      private val typeToken = TypeToken.getParameterized(List::class.java, clazz) as TypeToken<List<T>>

      override fun extractResult(response: GithubApiResponse): List<T> = parseJsonResponse(response, typeToken)
    }

    open class JsonPage<T>(url: String, clazz: Class<T>, acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE)
      : Get<GithubResponsePage<T>>(url, acceptMimeType) {

      private val typeToken = TypeToken.getParameterized(List::class.java, clazz) as TypeToken<List<T>>

      override fun extractResult(response: GithubApiResponse): GithubResponsePage<T> {
        return GithubResponsePage.parseFromHeader(parseJsonResponse(response, typeToken),
                                                  response.findHeader(GithubResponsePage.HEADER_NAME))
      }
    }

    open class JsonSearchPage<T>(url: String, clazz: Class<T>, acceptMimeType: String? = GithubApiContentHelper.V3_JSON_MIME_TYPE)
      : Get<GithubResponsePage<T>>(url, acceptMimeType) {

      private val typeToken = TypeToken.getParameterized(GithubSearchResult::class.java, clazz) as TypeToken<GithubSearchResult<T>>

      override fun extractResult(response: GithubApiResponse): GithubResponsePage<T> {
        return GithubResponsePage.parseFromHeader(parseJsonResponse(response, typeToken).items,
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

  abstract class Post<T> @JvmOverloads constructor(override val body: String,
                                                   override val bodyMimeType: String,
                                                   url: String,
                                                   override val acceptMimeType: String? = null) : GithubApiRequest.WithBody<T>(url) {
    companion object {
      inline fun <reified T> json(url: String, body: Any): Post<T> = Json(url, body, T::class.java)
    }

    open class Json<T>(url: String, body: Any, clazz: Class<T>) : Post<T>(GithubApiContentHelper.toJson(body),
                                                                          GithubApiContentHelper.JSON_MIME_TYPE,
                                                                          url,
                                                                          GithubApiContentHelper.V3_JSON_MIME_TYPE) {
      private val typeToken = TypeToken.get(clazz)

      override fun extractResult(response: GithubApiResponse): T = parseJsonResponse(response, typeToken)
    }
  }

  abstract class Put<T> @JvmOverloads constructor(final override val body: String?,
                                                  override val bodyMimeType: String,
                                                  url: String,
                                                  override val acceptMimeType: String? = null) : GithubApiRequest.WithBody<T>(url) {
    init {
      if (body == null) headers["Content-Length"] = "0"
    }

    companion object {
      inline fun <reified T> json(url: String, body: Any? = null): Put<T> = Json(url, body, T::class.java)

      inline fun <reified T> jsonList(url: String, body: Any): Put<List<T>> = JsonList(url, T::class.java, body)
    }

    open class Json<T>(url: String, body: Any?, clazz: Class<T>) : Put<T>(body?.let { GithubApiContentHelper.toJson(it) },
                                                                          GithubApiContentHelper.JSON_MIME_TYPE,
                                                                          url,
                                                                          GithubApiContentHelper.V3_JSON_MIME_TYPE) {
      private val typeToken = TypeToken.get(clazz)

      override fun extractResult(response: GithubApiResponse): T = parseJsonResponse(response, typeToken)
    }

    open class JsonList<T>(url: String, clazz: Class<T>, body: Any?)
      : Put<List<T>>(body?.let { GithubApiContentHelper.toJson(it) },
                     GithubApiContentHelper.JSON_MIME_TYPE,
                     url,
                     GithubApiContentHelper.V3_JSON_MIME_TYPE) {

      private val typeToken = TypeToken.getParameterized(List::class.java, clazz) as TypeToken<List<T>>

      override fun extractResult(response: GithubApiResponse): List<T> = parseJsonResponse(response, typeToken)
    }
  }

  abstract class Patch<T> @JvmOverloads constructor(override val body: String,
                                                    override val bodyMimeType: String,
                                                    url: String,
                                                    override val acceptMimeType: String? = null) : GithubApiRequest.WithBody<T>(url) {
    companion object {
      inline fun <reified T> json(url: String, body: Any): Post<T> = Json(url, body, T::class.java)
    }

    open class Json<T>(url: String, body: Any, clazz: Class<T>) : Post<T>(GithubApiContentHelper.toJson(body),
                                                                          GithubApiContentHelper.JSON_MIME_TYPE,
                                                                          url,
                                                                          GithubApiContentHelper.V3_JSON_MIME_TYPE) {
      private val typeToken = TypeToken.get(clazz)

      override fun extractResult(response: GithubApiResponse): T = parseJsonResponse(response, typeToken)
    }
  }

  abstract class Delete<T> @JvmOverloads constructor(override val body: String?,
                                                     override val bodyMimeType: String,
                                                     url: String,
                                                     override val acceptMimeType: String? = null) : GithubApiRequest.WithBody<T>(url) {

    companion object {
      inline fun <reified T> json(url: String, body: Any? = null): Delete<T> = Json(url, body, T::class.java)
    }

    open class Json<T>(url: String, body: Any? = null, clazz: Class<T>) : Delete<T>(body?.let { GithubApiContentHelper.toJson(it) },
                                                                                    GithubApiContentHelper.JSON_MIME_TYPE,
                                                                                    url,
                                                                                    GithubApiContentHelper.V3_JSON_MIME_TYPE) {
      private val typeToken = TypeToken.get(clazz)

      override fun extractResult(response: GithubApiResponse): T = parseJsonResponse(response, typeToken)
    }
  }

  companion object {
    private fun <T> parseJsonResponse(response: GithubApiResponse, typeToken: TypeToken<T>): T {
      return response.readBody(ThrowableConvertor { GithubApiContentHelper.readJson(it, typeToken) })
    }
  }
}
