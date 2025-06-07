// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.introspect.VisibilityChecker
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.exceptions.GithubJsonException
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO

object GithubApiContentHelper {
  const val JSON_MIME_TYPE = "application/json"
  const val V3_JSON_MIME_TYPE = "application/vnd.github.v3+json"
  const val V3_HTML_JSON_MIME_TYPE = "application/vnd.github.v3.html+json"
  const val V3_DIFF_JSON_MIME_TYPE = "application/vnd.github.v3.diff+json"

  private val jackson: ObjectMapper = jacksonObjectMapper().genericConfig()
    .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)

  private val gqlJackson: ObjectMapper = jacksonObjectMapper().genericConfig()
    .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)

  private fun ObjectMapper.genericConfig(): ObjectMapper = apply {
    setDateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"))
    setTimeZone(TimeZone.getDefault())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
    setSerializationInclusion(JsonInclude.Include.NON_NULL)
    setVisibility(VisibilityChecker.Std(JsonAutoDetect.Visibility.NONE,
                                        JsonAutoDetect.Visibility.NONE,
                                        JsonAutoDetect.Visibility.NONE,
                                        JsonAutoDetect.Visibility.NONE,
                                        JsonAutoDetect.Visibility.ANY))
    configOverride(List::class.java)
      .setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.AS_EMPTY))
  }

  @Throws(GithubJsonException::class)
  inline fun <reified T> fromJson(string: String): T = fromJson(string, T::class.java)

  @Throws(GithubJsonException::class)
  internal inline fun <reified T> fromJson(string: String, gqlNaming: Boolean = false): T = fromJson(string, T::class.java, gqlNaming)

  @JvmStatic
  @Throws(GithubJsonException::class)
  fun <T> fromJson(string: String, clazz: Class<T>, gqlNaming: Boolean = false): T {
    try {
      return getObjectMapper(gqlNaming).readValue(string, clazz)
    }
    catch (e: JsonParseException) {
      throw GithubJsonException("Can't parse GitHub response", e)
    }
  }

  @JvmStatic
  @Throws(GithubJsonException::class)
  fun <T> readJsonObject(reader: Reader, clazz: Class<T>, vararg parameters: Class<*>, gqlNaming: Boolean = false): T {
    return readJson(reader, jackson.typeFactory.constructParametricType(clazz, *parameters), gqlNaming)
  }

  @JvmStatic
  @Throws(GithubJsonException::class)
  fun <T> readJsonList(reader: Reader, parameterClass: Class<T>): List<T> {
    return readJson(reader, jackson.typeFactory.constructCollectionType(List::class.java, parameterClass))
  }

  @JvmStatic
  @Throws(GithubJsonException::class)
  fun <T, U> readJsonMap(reader: Reader, gqlNaming: Boolean = false): Map<T, U> {
    try {
      return getObjectMapper(gqlNaming).readValue(reader, object : TypeReference<Map<T, U>>() {})
    }
    catch (e: JsonProcessingException) {
      throw GithubJsonException("Can't parse GitHub response", e)
    }
  }

  @Throws(GithubJsonException::class)
  private fun <T> readJson(reader: Reader, type: JavaType, gqlNaming: Boolean = false): T {
    try {
      @Suppress("UNCHECKED_CAST")
      if (type.isTypeOrSubTypeOf(Unit::class.java) || type.isTypeOrSubTypeOf(Void::class.java)) return Unit as T
      return getObjectMapper(gqlNaming).readValue(reader, type)
    }
    catch (e: JsonProcessingException) {
      throw GithubJsonException("Can't parse GitHub response", e)
    }
  }

  @JvmStatic
  @Throws(GithubJsonException::class)
  fun toJson(content: Any, gqlNaming: Boolean = false): String {
    try {
      return getObjectMapper(gqlNaming).writeValueAsString(content)
    }
    catch (e: JsonProcessingException) {
      throw GithubJsonException("Can't serialize GitHub request body", e)
    }
  }

  @VisibleForTesting
  internal fun getObjectMapper(gqlNaming: Boolean = false): ObjectMapper = if (!gqlNaming) jackson else gqlJackson

  @JvmStatic
  @Throws(IOException::class)
  fun loadImage(stream: InputStream): BufferedImage {
    return ImageIO.read(stream)
  }
}