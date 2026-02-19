// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.completion

import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.filter.EvaluationFilterReader
import com.intellij.cce.util.getAs
import com.intellij.cce.util.getIfExists
import java.lang.reflect.Type

class CompletionStrategySerializer : StrategySerializer<CompletionStrategy> {
  override fun serialize(src: CompletionStrategy, typeOfSrc: Type, context: JsonSerializationContext): JsonObject {
    val jsonObject = JsonObject()
    jsonObject.addProperty("type", src.completionType.name)
    val prefixObject = JsonObject()
    prefixObject.addProperty("name", prefixToString(src.prefix))
    if (src.prefix is CompletionPrefix.SimplePrefix) {
      prefixObject.addProperty("n", (src.prefix as CompletionPrefix.SimplePrefix).n)
    }
    jsonObject.add("prefix", prefixObject)
    jsonObject.addProperty("context", src.context.name)
    val filtersObject = JsonObject()
    src.filters.forEach { id, filter -> filtersObject.add(id, filter.toJson()) }
    jsonObject.add("filters", filtersObject)
    return jsonObject
  }

  override fun deserialize(map: Map<String, Any>, language: String): CompletionStrategy {
    val type = CompletionType.valueOf(map.getAs("type"))
    val prefix = getPrefix(map)
    val context = CompletionContext.valueOf(map.getAs("context"))
    val filters = EvaluationFilterReader.readFilters(map.getIfExists<Map<String, Any>>("filters"), language)
    return CompletionStrategy(type, prefix, context, filters)
  }

  private fun prefixToString(prefix: CompletionPrefix): String = when (prefix) {
    is CompletionPrefix.NoPrefix -> "NoPrefix"
    is CompletionPrefix.SimplePrefix -> "SimplePrefix"
    is CompletionPrefix.CapitalizePrefix -> "CapitalizePrefix"
  }

  private fun getPrefix(strategy: Map<String, Any>): CompletionPrefix {
    val prefix = strategy.getAs<Map<String, Any>>("prefix")
    return when (prefix["name"]) {
      "NoPrefix" -> CompletionPrefix.NoPrefix
      "CapitalizePrefix" -> CompletionPrefix.CapitalizePrefix
      "SimplePrefix" -> CompletionPrefix.SimplePrefix(prefix.getAs<Double>("n").toInt())
      else -> throw IllegalArgumentException("Unknown completion prefix")
    }
  }
}