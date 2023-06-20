// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.golf //// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.intellij.cce.evaluable.StrategySerializer
import java.lang.reflect.Type

class LineCompletionStrategySerializer : StrategySerializer<CompletionGolfStrategy> {
  override fun serialize(src: CompletionGolfStrategy, typeOfSrc: Type, context: JsonSerializationContext): JsonObject {
    return gson.toJsonTree(src) as JsonObject
  }

  override fun deserialize(map: Map<String, Any>, language: String): CompletionGolfStrategy {
    return gson.fromJson(gson.toJson(map), CompletionGolfStrategy::class.java)
  }

  companion object {
    private val gson = Gson()
  }
}