// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.exc.StreamReadException
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.DatabindException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import java.io.IOException
import java.io.Reader
import java.io.Writer

object SerializationHelper {
  private val SERIALIZATION_TO_WRITER_MAPPER: JsonMapper by lazy {
    val printer: DefaultPrettyPrinter = CustomPrettyPrinter()

    JsonMapper
      .builder()
      .enable(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS)
      .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      .serializationInclusion(JsonInclude.Include.NON_NULL)
      .defaultPrettyPrinter(printer)
      .build()
  }

  private val SERIALIZATION_MAPPER: JsonMapper by lazy {
    val printer: DefaultPrettyPrinter = CustomPrettyPrinter()

    JsonMapper
      .builder()
      .addModule(kotlinModule())
      .enable(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS)
      .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      .serializationInclusion(JsonInclude.Include.NON_NULL)
      .defaultPrettyPrinter(printer)
      .build()
  }

  private val SERIALIZATION_TO_SINGLE_LINE_MAPPER: JsonMapper by lazy {
    JsonMapper
      .builder()
      .addModule(kotlinModule())
      .enable(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS)
      .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      .serializationInclusion(JsonInclude.Include.NON_NULL)
      .build()
  }

  private val DESERIALIZATION_MAPPER: JsonMapper by lazy {
    JsonMapper
      .builder()
      .enable(DeserializationFeature.USE_LONG_FOR_INTS)
      .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .build()
  }

  private val DESERIALIZATION_WITH_KOTLIN_MAPPER: JsonMapper by lazy {
    JsonMapper
      .builder()
      .addModule(kotlinModule())
      .enable(DeserializationFeature.USE_LONG_FOR_INTS)
      .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .build()
  }

  /**
   * Method that can be used to serialize any Java value as JSON output, using Writer provided.
   */
  fun serialize(w: Writer, value: Any) {
    SERIALIZATION_TO_WRITER_MAPPER.writerWithDefaultPrettyPrinter().writeValue(w, value)
  }

  /**
   * Method that can be used to serialize any Java value as a String.
   *
   * @throws JsonProcessingException
   */
  @Throws(JsonProcessingException::class)
  fun serialize(value: Any?): String {
    return SERIALIZATION_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value)
  }

  /**
   * Method that can be used to serialize any Java value as a String to single line.
   *
   * @throws JsonProcessingException
   */
  @Throws(JsonProcessingException::class)
  fun serializeToSingleLine(value: Any?): String {
    return SERIALIZATION_TO_SINGLE_LINE_MAPPER.writeValueAsString(value)
  }

  /**
   * Method that can be used to deserialize any Java value, using Reader provided.
   *
   * @throws IOException
   * @throws StreamReadException
   * @throws DatabindException
   */
  fun <T> deserialize(src: Reader, valueType: Class<T>): T {
    return DESERIALIZATION_MAPPER.readValue(src, valueType)
  }

  /**
   * Method to deserialize JSON content from given JSON content String to given class.
   *
   * @throws StreamReadException if underlying input contains invalid content
   *    of type {@link JsonParser} supports (JSON for default case)
   * @throws DatabindException if the input JSON structure does not match structure
   *   expected for result type (or has other mismatch issues)
   */
  @kotlin.jvm.Throws(StreamReadException::class, DatabindException::class)
  fun <T> deserialize(json: String, clazz: Class<T>): T {
    return DESERIALIZATION_WITH_KOTLIN_MAPPER.readValue(json, clazz)
  }
}

private class CustomPrettyPrinter : DefaultPrettyPrinter {
  init {
    _objectIndenter = DefaultIndenter("  ", "\n")
    _arrayIndenter = DefaultIndenter("  ", "\n")
  }

  constructor() : super()
  constructor(base: DefaultPrettyPrinter?) : super(base)

  override fun writeObjectFieldValueSeparator(g: JsonGenerator) {
    g.writeRaw(": ")
  }

  override fun writeEndArray(g: JsonGenerator, nrOfValues: Int) {
    if (!_arrayIndenter.isInline) {
      --_nesting
    }
    if (nrOfValues > 0) {
      _arrayIndenter.writeIndentation(g, _nesting)
    }
    g.writeRaw(']')
  }

  override fun writeEndObject(g: JsonGenerator, nrOfEntries: Int) {
    if (!_objectIndenter.isInline) {
      --_nesting
    }
    if (nrOfEntries > 0) {
      _objectIndenter.writeIndentation(g, _nesting)
    }
    g.writeRaw('}')
  }

  override fun createInstance(): DefaultPrettyPrinter {
    return CustomPrettyPrinter(this)
  }
}