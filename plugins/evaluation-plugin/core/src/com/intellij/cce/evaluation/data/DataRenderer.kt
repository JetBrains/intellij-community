package com.intellij.cce.evaluation.data

import com.google.gson.*
import java.lang.reflect.Type

/**
 * Represents a way of rendering raw data of a certain type.
 */
sealed interface DataRenderer<in T> {
  val serialName: String

  fun skip(value: T): Boolean = false

  data object InlineBoolean : DataRenderer<Boolean> {
    override val serialName: String = "inline_boolean"
  }

  data object InlineLong : DataRenderer<Long> {
    override val serialName: String = "inline_long"
  }

  data object InlineDouble : DataRenderer<Double> {
    override val serialName: String = "inline_double"
  }

  data class Text(val wrapping: Boolean = false, val showEmpty: Boolean = false) : DataRenderer<String> {
    override val serialName: String = "text"
    override fun skip(value: String): Boolean = !showEmpty && value.isBlank()
  }

  data object Lines : DataRenderer<List<String>> {
    override val serialName: String = "lines"
  }

  data object TextDiff : DataRenderer<TextUpdate> {
    override val serialName: String = "text_diff"
  }

  class Serializer : JsonSerializer<DataRenderer<*>>, JsonDeserializer<DataRenderer<*>> {
    override fun serialize(src: DataRenderer<*>?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
      val serialized = context?.serialize(src)
      // work around to serialize objects correctly
      return if (src == null || serialized?.asJsonObject?.get("serialName") != null) serialized else
        JsonObject().also { it.addProperty("serialName", src.serialName) }
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): DataRenderer<*>? {
      val type = json?.asJsonObject?.get("serialName")?.asString ?: return null
      return when (type) {
        "inline_boolean" -> InlineBoolean
        "inline_long" -> InlineLong
        "inline_double" -> InlineDouble
        "text" -> context?.deserialize(json, Text::class.java)
        "lines" -> Lines
        "text_diff" -> TextDiff
        else -> throw IllegalArgumentException("Unknown type: $type")
      }
    }
  }
}

interface TextUpdate {
  val originalText: String
  val updatedText: String

  companion object {
    operator fun invoke(originalText: String, updatedText: String): TextUpdate = Impl(originalText, updatedText)
  }

  private class Impl(override val originalText: String, override val updatedText: String) : TextUpdate
}

data class FileUpdate(val filePath: String, override val originalText: String, override val updatedText: String) : TextUpdate