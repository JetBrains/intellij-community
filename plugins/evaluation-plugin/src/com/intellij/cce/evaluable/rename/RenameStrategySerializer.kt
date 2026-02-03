// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.rename

import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.filter.EvaluationFilterReader
import com.intellij.cce.util.getIfExists
import com.intellij.cce.util.getOrThrow
import java.lang.reflect.Type

class RenameStrategySerializer : StrategySerializer<RenameStrategy> {
  override fun serialize(src: RenameStrategy, typeOfSrc: Type, context: JsonSerializationContext): JsonObject {
    val jsonObject = JsonObject()
    jsonObject.addProperty("collectContextOnly", src.collectContextOnly)
    jsonObject.addProperty("placeholderName", src.placeholderName)
    jsonObject.addProperty("suggestionsProvider", src.suggestionsProvider)
    val filtersObject = JsonObject()
    src.filters.forEach { id, filter -> filtersObject.add(id, filter.toJson()) }
    jsonObject.add("filters", filtersObject)
    return jsonObject
  }

  override fun deserialize(map: Map<String, Any>, language: String): RenameStrategy {
    val collectContextOnly = map.getOrThrow<Boolean>("collectContextOnly")
    val placeholderName = map.getOrThrow<String>("placeholderName")
    val suggestionsProvider = map.getOrThrow<String>("suggestionsProvider")
    val filters = EvaluationFilterReader.readFilters(map.getIfExists<Map<String, Any>>("filters"), language)
    return RenameStrategy(collectContextOnly, placeholderName, suggestionsProvider, filters)
  }
}