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

  data object InlineInt : DataRenderer<Int> {
    override val serialName: String = "inline_int"
  }

  data object NamedRanges : DataRenderer<List<NamedRange>> {
    override val serialName: String = "named_ranges"
  }

  data object ClickableLink : DataRenderer<String> {
    override val serialName: String = "clickable_link"
  }

  data class Text(val wrapping: Boolean = false, val showEmpty: Boolean = false) : DataRenderer<String> {
    override val serialName: String = "text"
    override fun skip(value: String): Boolean = !showEmpty && value.isBlank()
  }

  data object Lines : DataRenderer<List<String>> {
    override val serialName: String = "lines"
  }

  data object Snippets : DataRenderer<List<String>> {
    override val serialName: String = "snippets"
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
        "inline_int" -> InlineInt
        "named_ranges" -> NamedRanges
        "clickable_link" -> ClickableLink
        "text" -> context?.deserialize(json, Text::class.java)
        "lines" -> Lines
        "text_diff" -> TextDiff
        "snippets" -> Snippets
        else -> throw IllegalArgumentException("Unknown type: $type")
      }
    }
  }
}

interface HasDescription {
  val descriptionText: String
}

interface TextUpdate {
  val originalText: String
  val updatedText: String

  companion object {
    operator fun invoke(originalText: String, updatedText: String): TextUpdate = Impl(originalText, updatedText)
  }

  private class Impl(override val originalText: String, override val updatedText: String) : TextUpdate
}

data class FileUpdate(val filePath: String, override val originalText: String, override val updatedText: String) : TextUpdate, HasDescription {
  override val descriptionText: String = filePath
}

interface Range {
  val start: Int
  val end: Int
}

data class NamedRange(override val start: Int, override val end: Int, val text: String) : Range