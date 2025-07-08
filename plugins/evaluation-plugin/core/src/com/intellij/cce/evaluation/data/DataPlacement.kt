package com.intellij.cce.evaluation.data

import com.google.gson.*
import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.Suggestion
import com.intellij.cce.core.SuggestionSource
import java.lang.reflect.Type

/**
 * Represents a format in which data should be stored.
 * The class defines how the data is serialized and deserialized.
 * Due to historical reasons a [Lookup] instance is currently the primary format for serialization.
 */
sealed interface DataPlacement<In, Out> {

  /**
   * [DataPlacement] should be serializable itself so we could track which data has been saved.
   */
  val serialName: String

  /**
   * Since part of the data can be re-used (like a current file text),
   * there is no need to always separately store all data.
   * In these cases deserializable data can be obtained directly from [DataProps].
   */
  fun dump(lookup: Lookup, t: In): Lookup

  /**
   * A list is returned to allow flexibility for handling multiple outputs when necessary.
   * For example, multiple changed files during code generation can be processed as separate entities.
   * Missing data is represented by an empty list.
   */
  fun restore(props: DataProps): List<Out>

  fun first(props: DataProps): Out? = restore(props).firstOrNull()

  data class AdditionalText(val propertyKey: String) : DataPlacement<String, String> {
    override val serialName: String = "additional_text"
    override fun dump(lookup: Lookup, t: String): Lookup = lookup.copy(additionalInfo = lookup.additionalInfo + mapOf(propertyKey to t))
    override fun restore(props: DataProps): List<String> = listOfNotNull(props.lookup.additionalInfo[propertyKey] as? String)
  }

  data class AdditionalBoolean(val propertyKey: String) : DataPlacement<Boolean, Boolean> {
    override val serialName: String = "additional_boolean"
    override fun dump(lookup: Lookup, t: Boolean): Lookup = lookup.copy(additionalInfo = lookup.additionalInfo + mapOf(propertyKey to t))
    override fun restore(props: DataProps): List<Boolean> = listOfNotNull(props.lookup.additionalInfo[propertyKey] as? Boolean)
  }

  data class AdditionalDouble(val propertyKey: String) : DataPlacement<Double, Double> {
    override val serialName: String = "additional_double"
    override fun dump(lookup: Lookup, t: Double): Lookup = lookup.copy(additionalInfo = lookup.additionalInfo + mapOf(propertyKey to t))
    override fun restore(props: DataProps): List<Double> = listOfNotNull(props.lookup.additionalInfo[propertyKey] as? Double)
  }

  data class AdditionalInt(val propertyKey: String) : DataPlacement<Int, Int> {
    override val serialName: String = "additional_int"
    override fun dump(lookup: Lookup, t: Int): Lookup =
      // because of json serializes int to double we cast it by ourselves for predictability
      lookup.copy(additionalInfo = lookup.additionalInfo + mapOf(propertyKey to t.toDouble()))
    override fun restore(props: DataProps): List<Int> = listOfNotNull(props.lookup.additionalInfo[propertyKey] as? Double).map { it.toInt() }
  }

  data class AdditionalJsonSerializedStrings(val propertyKey: String) : DataPlacement<List<String>, List<String>> {
    override val serialName: String = "additional_concatenated_snippets"
    override fun dump(lookup: Lookup, t: List<String>): Lookup {
      return lookup.copy(additionalInfo = lookup.additionalInfo + mapOf(propertyKey to gson.toJson(t)))
    }

    override fun restore(props: DataProps): List<List<String>> {
      val rawString = props.lookup.additionalInfo[propertyKey] ?: return emptyList()
      return listOf(gson.fromJson(rawString as String, Array<String>::class.java).toList())
    }
  }

  data class AdditionalConcatenatedLines(val propertyKey: String) : DataPlacement<List<String>, List<String>> {
    override val serialName: String = "additional_concatenated_lines"

    override fun dump(lookup: Lookup, t: List<String>): Lookup {
      check(t.all { !it.contains("\n") }) { t }

      val text = t.joinToString("\n").takeIf { it.isNotEmpty() } ?: return lookup
      return lookup.copy(additionalInfo = lookup.additionalInfo + mapOf(propertyKey to text))
    }

    override fun restore(props: DataProps): List<List<String>> {
      val text = props.lookup.additionalInfo[propertyKey] ?: return emptyList()
      return listOf((text as String).split("\n").filter { it.isNotEmpty() })
    }
  }

  data object Latency : DataPlacement<Long, Long> {
    override val serialName: String = "latency"

    override fun dump(lookup: Lookup, t: Long): Lookup = lookup.copy(latency = t)

    override fun restore(props: DataProps): List<Long> = listOf(props.lookup.latency)
  }

  data object CurrentFileUpdate : DataPlacement<String, TextUpdate> {
    override val serialName: String = "current_file_update"

    override fun dump(lookup: Lookup, t: String): Lookup {
      return lookup.copy(
        suggestions = lookup.suggestions + Suggestion(t, t, SuggestionSource.INTELLIJ, isRelevant = false)
      )
    }

    override fun restore(props: DataProps): List<TextUpdate> =
      props.lookup.suggestions.map { TextUpdate(props.currentFileContent ?: "", it.presentationText) }
  }

  data class FileUpdates(val propertyKey: String) : DataPlacement<List<FileUpdate>, FileUpdate> {
    override val serialName: String = "file_updates"

    override fun dump(lookup: Lookup, t: List<FileUpdate>): Lookup {
      return lookup.copy(
        additionalInfo = lookup.additionalInfo + Pair(propertyKey, gson.toJsonTree(t))
      )
    }

    override fun restore(props: DataProps): List<FileUpdate> {
      val rawUpdates = props.lookup.additionalInfo[propertyKey] ?: return emptyList()
      val updates = rawUpdates as? JsonElement ?: gson.toJsonTree(rawUpdates)
      return gson.fromJson(updates, Array<FileUpdate>::class.java).toList()
    }
  }

  data class AdditionalNamedRanges(val propertyKey: String) : DataPlacement<List<NamedRange>, List<NamedRange>> {
    override val serialName: String = "named_range"

    override fun dump(lookup: Lookup, t: List<NamedRange>): Lookup {
      return lookup.copy(
        additionalInfo = lookup.additionalInfo + Pair(propertyKey, gson.toJsonTree(t))
      )
    }

    override fun restore(props: DataProps): List<List<NamedRange>> {
      val namedRanges = props.lookup.additionalInfo[propertyKey] ?: return emptyList()
      val ranges = namedRanges as? JsonElement ?: gson.toJsonTree(namedRanges)
      return listOf(gson.fromJson(ranges, Array<NamedRange>::class.java).toList())
    }
  }

  class Serializer : JsonSerializer<DataPlacement<*, *>>, JsonDeserializer<DataPlacement<*, *>> {
    override fun serialize(src: DataPlacement<*, *>?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
      val serialized = context?.serialize(src)
      // work around to serialize objects correctly
      return if (src == null || serialized?.asJsonObject?.get("serialName") != null) serialized else
        JsonObject().also { it.addProperty("serialName", src.serialName) }
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): DataPlacement<*, *>? {
      val type = json?.asJsonObject?.get("serialName")?.asString ?: return null
      return when (type) {
        "additional_text" -> context?.deserialize(json, AdditionalText::class.java)
        "additional_boolean" -> context?.deserialize(json, AdditionalBoolean::class.java)
        "additional_double" -> context?.deserialize(json, AdditionalDouble::class.java)
        "additional_int" -> context?.deserialize(json, AdditionalInt::class.java)
        "named_range" -> context?.deserialize(json, AdditionalNamedRanges::class.java)
        "additional_concatenated_lines" -> context?.deserialize(json, AdditionalConcatenatedLines::class.java)
        "additional_concatenated_snippets" -> context?.deserialize(json, AdditionalJsonSerializedStrings::class.java)
        "latency" -> Latency
        "current_file_update" -> CurrentFileUpdate
        "file_updates" -> context?.deserialize(json, FileUpdates::class.java)
        else -> throw IllegalArgumentException("Unknown type: $type")
      }
    }
  }

  companion object {
    private val gson = Gson()
  }
}

data class DataProps(
  val currentFileName: String?,
  val currentFileContent: String?,
  val session: Session,
  val lookup: Lookup
) {
  init {
    check(session.lookups.contains(lookup))
  }
}