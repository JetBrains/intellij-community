// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.google.gson.reflect.TypeToken
import com.intellij.util.ThrowableConvertor
import java.io.IOException

/**
 * Represents an API request with strictly defined response type
 */
sealed class GithubApiRequest<T>(val url: String) {
  var operationName: String? = null
  abstract val acceptMimeType: String?
  @Throws(IOException::class)
  abstract fun extractResult(response: GithubApiResponse): T

  fun withOperationName(name: String): GithubApiRequest<T> {
    operationName = name
    return this
  }

  abstract class Get<T> @JvmOverloads constructor(url: String,
                                                  override val acceptMimeType: String? = null) : GithubApiRequest<T>(url) {
    companion object {
      inline fun <reified T> json(url: String): Get<T> = Json(url, T::class.java)
    }

    open class Json<T>(url: String, clazz: Class<T>) : Get<T>(url, GithubApiContentHelper.V3_JSON_MIME_TYPE) {
      private val typeToken = TypeToken.get(clazz)

      override fun extractResult(response: GithubApiResponse): T = parseJsonResponse(response, typeToken)
    }
  }

  abstract class Head<T> @JvmOverloads constructor(url: String,
                                                   override val acceptMimeType: String? = null) : GithubApiRequest<T>(url)

  abstract class WithBody<T>(url: String) : GithubApiRequest<T>(url) {
    abstract val body: String
    abstract val bodyMimeType: String
  }

  abstract class Post<T> @JvmOverloads constructor(override val body: String,
                                                   override val bodyMimeType: String,
                                                   url: String,
                                                   override val acceptMimeType: String? = null) : GithubApiRequest.WithBody<T>(url)

  companion object {
    private fun <T> parseJsonResponse(response: GithubApiResponse, typeToken: TypeToken<T>): T {
      return response.readBody(ThrowableConvertor { GithubApiContentHelper.readJson(it, typeToken) })
    }
  }
}
