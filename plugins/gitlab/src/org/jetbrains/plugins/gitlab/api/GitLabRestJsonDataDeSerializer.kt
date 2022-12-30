// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.introspect.VisibilityChecker
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.collaboration.api.json.JsonDataDeserializer
import com.intellij.collaboration.api.json.JsonDataSerializer
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.*

object GitLabRestJsonDataDeSerializer : JsonDataSerializer, JsonDataDeserializer {

  private val mapper: ObjectMapper = jacksonObjectMapper()
    .genericConfig()
    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

  internal fun ObjectMapper.genericConfig(): ObjectMapper =
    this.setDateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"))
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

  override fun toJsonBytes(content: Any): ByteArray = mapper.writeValueAsBytes(content)

  override fun <T> fromJson(bodyReader: Reader, clazz: Class<T>): T = mapper.readValue(bodyReader, clazz)

  override fun <T> fromJson(bodyReader: Reader, clazz: Class<T>, vararg classArgs: Class<*>): T =
    mapper.readValue(bodyReader, mapper.typeFactory.constructParametricType(clazz, *classArgs))
}
