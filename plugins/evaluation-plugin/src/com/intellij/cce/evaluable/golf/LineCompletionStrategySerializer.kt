// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.golf //// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.intellij.cce.core.SuggestionSource
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.evaluable.completion.CompletionType
import com.intellij.cce.util.getIfExists
import java.lang.reflect.Type

class LineCompletionStrategySerializer : StrategySerializer<CompletionGolfStrategy> {
  override fun serialize(src: CompletionGolfStrategy, typeOfSrc: Type, context: JsonSerializationContext): JsonObject {
    return gson.toJsonTree(src) as JsonObject
  }

  override fun deserialize(map: Map<String, Any>, language: String): CompletionGolfStrategy {
    val mode: CompletionGolfMode = CompletionGolfMode.valueOf(map.get("mode") as String)
    val builder = CompletionGolfStrategy.Builder(mode)
    map.getIfExists<Boolean>("checkLine")?.let { builder.checkLine = it }
    map.getIfExists<Boolean>("invokeOnEachChar")?.let { builder.invokeOnEachChar = it }
    map.getIfExists<Boolean>("checkToken")?.let { builder.checkToken = it }
    map.getIfExists<String>("source")?.let { builder.source = SuggestionSource.valueOf(it) }
    map.getIfExists<Double>("topN")?.let { builder.topN = it.toInt()}
    map.getIfExists<String>("suggestionsProvider")?.let { builder.suggestionsProvider = it }
    map.getIfExists<String>("pathToZipModel")?.let { builder.pathToZipModel = it }
    map.getIfExists<String>("completionType")?.let { builder.completionType = CompletionType.valueOf(it) }
    return builder.build()
  }

  companion object {
    private val gson = Gson()
  }
}