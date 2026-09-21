// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.intellij.collaboration.api.json.JsonDataDeserializer
import com.intellij.collaboration.api.json.JsonDataSerializer
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.io.InputStream
import java.io.Reader
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.TimeZone

object GitLabRestJsonDataDeSerializer : JsonDataSerializer, JsonDataDeserializer {

  private val mapper: ObjectMapper = gitlabJacksonMapperBuilder()
    .defaultDateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"))
    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    .build()

  internal fun gitlabJacksonMapperBuilder(): JsonMapper.Builder =
    jacksonMapperBuilder { disable(KotlinFeature.StrictNullChecks) }
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      // Jackson 3 enables this feature by default. With it, the Kotlin module marks every non-nullable primitive constructor parameter
      // as required, and GitLab omits such fields (e.g. `mergeable` in the merge request list). Keep the Jackson 2 behavior: an absent
      // primitive gets its default value (IJPL-256217).
      .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
      .configure(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)
      .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
      .changeDefaultPropertyInclusion { JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL) }
      .changeDefaultVisibility {
        it.withGetterVisibility(JsonAutoDetect.Visibility.NONE)
          .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
          .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
          .withCreatorVisibility(JsonAutoDetect.Visibility.NONE)
          .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
      }
      .defaultTimeZone(TimeZone.getDefault())

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

  override fun <T : Any> readJson(stream: InputStream, charset: Charset, clazz: Class<T>): T? {
    return if (isCharsetAllowed(charset)) {
      mapper.createParser(stream)
        .readValueAsTree<JsonNode>()
        ?.let { mapper.treeToValue(it, clazz) }
    }
    else {
      fromJson(stream.reader(charset), clazz)
    }
  }

  override fun <T : Any> readJson(
    stream: InputStream,
    charset: Charset,
    clazz: Class<T>,
    vararg classArgs: Class<*>,
  ): T? {
    return if (isCharsetAllowed(charset)) {
      val type = mapper.typeFactory.constructParametricType(clazz, *classArgs)
      mapper.createParser(stream)
        .readValueAsTree<JsonNode>()
        ?.let { mapper.treeToValue(it, type) }
    }
    else {
      fromJson(stream.reader(charset), clazz, *classArgs)
    }
  }
}

private fun isCharsetAllowed(charset: Charset): Boolean =
  charset == Charsets.UTF_8
  || charset == Charsets.UTF_16
  || charset == Charsets.UTF_32
