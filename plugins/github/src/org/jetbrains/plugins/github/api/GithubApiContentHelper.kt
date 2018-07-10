// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import org.jetbrains.io.mandatory.NullCheckingFactory
import org.jetbrains.plugins.github.exceptions.GithubJsonException
import java.io.IOException

object GithubApiContentHelper {
  const val JSON_MIME_TYPE = "application/json"
  const val V3_JSON_MIME_TYPE = "application/vnd.github.v3+json"

  val gson: Gson = GsonBuilder()
    .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .registerTypeAdapterFactory(NullCheckingFactory.INSTANCE)
    .create()

  @Throws(GithubJsonException::class)
  inline fun <reified T> fromJson(string: String): T = fromJson(string, T::class.java)

  @JvmStatic
  @Throws(GithubJsonException::class)
  fun <T> fromJson(string: String, clazz: Class<T>): T {
    try {
      return gson.fromJson(string, TypeToken.get(clazz).type)
    }
    catch (e: JsonParseException) {
      throw GithubJsonException("Couldn't parse GitHub response", e)
    }
  }
}