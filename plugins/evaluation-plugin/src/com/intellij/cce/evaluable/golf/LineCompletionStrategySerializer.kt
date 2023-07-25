// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.golf //// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.intellij.cce.core.SuggestionSource
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.evaluable.completion.CompletionType
import java.lang.reflect.Type

class LineCompletionStrategySerializer : StrategySerializer<CompletionGolfStrategy> {
  override fun serialize(src: CompletionGolfStrategy, typeOfSrc: Type, context: JsonSerializationContext): JsonObject {
    return gson.toJsonTree(src) as JsonObject
  }

  override fun deserialize(map: Map<String, Any>, language: String): CompletionGolfStrategy {
    val mode: CompletionGolfMode = CompletionGolfMode.valueOf(map.get("mode") as String)
    val checkLine: Boolean = if (map.containsKey("checkLine")) {
      map.get("checkLine") as Boolean
    } else {
      true
    }
    val invokeOnEachChar: Boolean = if (map.containsKey("invokeOnEachChar")) {
      map.get("invokeOnEachChar") as Boolean
    } else {
      true
    }
    val checkToken: Boolean = if (map.containsKey("checkToken")) {
      map.get("checkToken") as Boolean
    } else {
      true
    }
    val source: SuggestionSource? = if (map.containsKey("source")) {
      SuggestionSource.valueOf(map.get("source") as String)
    } else {
      null
    }
    val topN: Int = if (map.containsKey("topN")) {
      map.get("topN") as Int
    } else {
      -1
    }
    val suggestionsProvider: String = if (map.containsKey("suggestionsProvider")) {
      map.get("suggestionsProvider") as String
    } else {
      CompletionGolfStrategy.DEFAULT_PROVIDER
    }
    val pathToZipModel: String? = if (map.containsKey("pathToZipModel")) {
      map.get("pathToZipModel") as String
    } else {
      null
    }
    val completionType: CompletionType = if (map.containsKey("completionType")) {
      CompletionType.valueOf(map.get("completionType") as String)
    } else {
      CompletionType.ML
    }
    return CompletionGolfStrategy(
      mode = mode,
      checkLine = checkLine,
      invokeOnEachChar = invokeOnEachChar,
      checkToken = checkToken,
      source = source,
      topN = topN,
      suggestionsProvider = suggestionsProvider,
      pathToZipModel = pathToZipModel,
      completionType = completionType
    )
  }

  companion object {
    private val gson = Gson()
  }
}