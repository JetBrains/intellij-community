package com.intellij.cce.evaluation.data

import com.google.gson.*
import java.lang.reflect.Type

/**
 * This class should be used in situations when we want to refer to evaluation data on context-dependent name.
 */
sealed interface DynamicName<in T> {
  val serialName: String

  fun resolve(props: DataProps, value: T): String?

  data object CurrentFileName : DynamicName<Any> {
    override val serialName: String = "current_file_name"
    override fun resolve(props: DataProps, value: Any): String? = props.currentFileName
  }

  data object FileName : DynamicName<FileUpdate> {
    override val serialName: String = "file_name"
    override fun resolve(props: DataProps, value: FileUpdate): String? = value.filePath.substringAfterLast('/')
  }

  data object ColoredInsightsFileName : DynamicName<ColoredInsightsData> {
    override val serialName: String = "colored_insights_file_name"
    override fun resolve(props: DataProps, value: ColoredInsightsData): String? = value.filePath.substringAfterLast('/')
  }

  data class Formatted<T>(val prefix: String, val name: DynamicName<T>, val suffix: String = "") : DynamicName<T> {
    override val serialName: String = "formatted"
    override fun resolve(props: DataProps, value: T): String? = name.resolve(props, value)?.let { "$prefix$it$suffix" }
  }

  class Serializer : JsonSerializer<DynamicName<*>>, JsonDeserializer<DynamicName<*>> {
    override fun serialize(src: DynamicName<*>?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
      val serialized = context?.serialize(src)
      // work around to serialize objects correctly
      return if (src == null || serialized?.asJsonObject?.get("serialName") != null) serialized else
        JsonObject().also { it.addProperty("serialName", src.serialName) }
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): DynamicName<*>? {
      val type = json?.asJsonObject?.get("serialName")?.asString ?: return null
      when (type) {
        "current_file_name" -> return context?.deserialize(json, CurrentFileName::class.java)
        "file_name" -> return context?.deserialize(json, FileName::class.java)
        "colored_insights_file_name" -> return context?.deserialize(json, ColoredInsightsFileName::class.java)
        "formatted" -> return context?.deserialize(json, Formatted::class.java)
        else -> throw IllegalArgumentException("Unknown type: $type")
      }
    }
  }
}