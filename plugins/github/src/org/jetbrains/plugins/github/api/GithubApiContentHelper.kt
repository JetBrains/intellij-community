// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.google.common.io.ByteStreams
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import org.jetbrains.io.mandatory.NullCheckingFactory
import org.jetbrains.plugins.github.exceptions.GithubJsonException
import java.awt.Image
import java.awt.Toolkit
import java.io.IOException
import java.io.InputStream
import java.io.Reader

object GithubApiContentHelper {
  const val JSON_MIME_TYPE = "application/json"
  const val V3_JSON_MIME_TYPE = "application/vnd.github.v3+json"
  const val V3_HTML_JSON_MIME_TYPE = "application/vnd.github.v3.html+json"

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

  @JvmStatic
  @Throws(GithubJsonException::class)
  fun <T> readJson(reader: Reader, typeToken: TypeToken<T>): T {
    try {
      return gson.fromJson(reader, typeToken.type)
    }
    catch (e: JsonParseException) {
      throw GithubJsonException("Couldn't parse GitHub response", e)
    }
  }

  @JvmStatic
  @Throws(GithubJsonException::class)
  fun toJson(content: Any): String {
    try {
      return gson.toJson(content)
    }
    catch (e: JsonIOException) {
      throw GithubJsonException("Couldn't serialize GitHub request body", e)
    }
  }

  @JvmStatic
  @Throws(IOException::class)
  fun loadImage(stream: InputStream): Image {
    val bytes = ByteStreams.toByteArray(stream)
    return Toolkit.getDefaultToolkit().createImage(bytes)
  }
}