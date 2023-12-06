// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable

import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

interface StrategySerializer<T : EvaluationStrategy> : JsonSerializer<T> {
  override fun serialize(src: T, typeOfSrc: Type, context: JsonSerializationContext): JsonObject
  fun deserialize(map: Map<String, Any>, language: String): T
}
