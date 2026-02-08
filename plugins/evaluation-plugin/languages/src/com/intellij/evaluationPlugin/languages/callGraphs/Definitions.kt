package com.intellij.evaluationPlugin.languages.callGraphs

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

private class IntRangeSerializer : JsonSerializer<IntRange> {
  override fun serialize(src: IntRange, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
    val array = JsonArray()
    array.add(src.first)
    array.add(src.last)
    return array
  }
}

private class IntRangeDeserializer : JsonDeserializer<IntRange> {
  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): IntRange {
    return when {
      json.isJsonArray -> {
        val arr = json.asJsonArray
        arr[0].asInt..arr[1].asInt
      }
      json.isJsonObject -> {
        val obj = json.asJsonObject
        obj.get("first").asInt..obj.get("last").asInt
      }
      else -> throw JsonParseException("Unsupported IntRange JSON: $json")
    }
  }
}

data class CallGraphNodeLocation(
  val projectRootFilePath: String,
  val textRange: IntRange,
)

data class CallGraphNode(
  val address: CallGraphNodeLocation,
  val projectName: String,
  val id: String,
  val qualifiedName: String,
)

data class CallGraphEdge(
  val callerId: String,
  val calleeId: String,
)

data class CallGraph(
  val nodes: List<CallGraphNode>,
  val edges: List<CallGraphEdge>,
) {
  fun serialise(): String {
    val gson = GsonBuilder()
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
      .registerTypeAdapter(IntRange::class.java, IntRangeSerializer())
      .setPrettyPrinting()
      .create()
    return gson.toJson(this)
  }

  companion object {
    fun deserialise(text: String): CallGraph {
      val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(IntRange::class.java, IntRangeDeserializer())
        .create()
      return gson.fromJson(text, CallGraph::class.java)
    }
  }
}
