package com.intellij.cce.callGraphs

import com.google.gson.*
import java.lang.reflect.Type

private class IntRangeSerializer : JsonSerializer<IntRange> {
  override fun serialize(src: IntRange, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
    val array = JsonArray()
    array.add(src.first)
    array.add(src.last)
    return array
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
}
