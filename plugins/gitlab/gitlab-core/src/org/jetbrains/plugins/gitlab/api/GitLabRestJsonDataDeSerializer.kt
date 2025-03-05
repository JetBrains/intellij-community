// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.introspect.VisibilityChecker
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.intellij.collaboration.api.json.JsonDataDeserializer
import com.intellij.collaboration.api.json.JsonDataSerializer
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.*


object GitLabRestJsonDataDeSerializer : JsonDataSerializer, JsonDataDeserializer {

  private val mapper: ObjectMapper = gitlabJacksonMapper()
    .genericConfig()
    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

  internal fun gitlabJacksonMapper(): ObjectMapper =
    jacksonMapperBuilder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)
      .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
      .serializationInclusion(JsonInclude.Include.NON_NULL)
      .visibility(VisibilityChecker.Std(JsonAutoDetect.Visibility.NONE,
                                           JsonAutoDetect.Visibility.NONE,
                                           JsonAutoDetect.Visibility.NONE,
                                           JsonAutoDetect.Visibility.NONE,
                                           JsonAutoDetect.Visibility.ANY))
      .build()

  internal fun ObjectMapper.genericConfig(): ObjectMapper =
    this.setDateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"))
      .setTimeZone(TimeZone.getDefault())

  override fun toJsonBytes(content: Any): ByteArray = mapper.writeValueAsBytes(content)

  // this is required to handle empty reader/stream without an exception
  override fun <T> fromJson(bodyReader: Reader, clazz: Class<T>): T? =
    mapper.createParser(bodyReader)
      .readValueAsTree<JsonNode>()
      ?.let { mapper.treeToValue(it, clazz) }

  override fun <T> fromJson(bodyReader: Reader, clazz: Class<T>, vararg classArgs: Class<*>): T? {
    val type = mapper.typeFactory.constructParametricType(clazz, *classArgs)
    return mapper.createParser(bodyReader)
      .readValueAsTree<JsonNode>()
      ?.let { mapper.treeToValue(it, type) }
  }
}
