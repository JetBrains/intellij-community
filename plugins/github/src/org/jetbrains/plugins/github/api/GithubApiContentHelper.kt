// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.introspect.VisibilityChecker
import org.jetbrains.plugins.github.exceptions.GithubJsonException
import java.awt.Image
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.text.SimpleDateFormat
import javax.imageio.ImageIO

object GithubApiContentHelper {
  const val JSON_MIME_TYPE = "application/json"
  const val V3_JSON_MIME_TYPE = "application/vnd.github.v3+json"
  const val V3_HTML_JSON_MIME_TYPE = "application/vnd.github.v3.html+json"
  const val V3_DIFF_JSON_MIME_TYPE = "application/vnd.github.v3.diff+json"

  val jackson: ObjectMapper = ObjectMapper()
    .setDateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"))
    .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    .setVisibility(VisibilityChecker.Std(JsonAutoDetect.Visibility.NONE,
                                         JsonAutoDetect.Visibility.NONE,
                                         JsonAutoDetect.Visibility.NONE,
                                         JsonAutoDetect.Visibility.NONE,
                                         JsonAutoDetect.Visibility.ANY))


  @Throws(GithubJsonException::class)
  inline fun <reified T> fromJson(string: String): T = fromJson(string, T::class.java)

  @JvmStatic
  @Throws(GithubJsonException::class)
  fun <T> fromJson(string: String, clazz: Class<T>): T {
    try {
      return jackson.readValue(string, clazz)
    }
    catch (e: JsonParseException) {
      throw GithubJsonException("Can't parse GitHub response", e)
    }
  }

  @JvmStatic
  @Throws(GithubJsonException::class)
  fun <T> readJson(reader: Reader, type: JavaType): T {
    try {
      @Suppress("UNCHECKED_CAST")
      if (type.isTypeOrSubTypeOf(Unit::class.java) || type.isTypeOrSubTypeOf(Void::class.java)) return Unit as T
      return jackson.readValue(reader, type)
    }
    catch (e: JsonProcessingException) {
      throw GithubJsonException("Can't parse GitHub response", e)
    }
  }

  @JvmStatic
  @Throws(GithubJsonException::class)
  fun toJson(content: Any): String {
    try {
      return jackson.writeValueAsString(content)
    }
    catch (e: JsonProcessingException) {
      throw GithubJsonException("Can't serialize GitHub request body", e)
    }
  }

  @JvmStatic
  @Throws(IOException::class)
  fun loadImage(stream: InputStream): Image {
    return ImageIO.read(stream)
  }
}