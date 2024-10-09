package com.intellij.cce.evaluable.conflictResolution

import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.filter.EvaluationFilter
import java.lang.reflect.Type

class ConflictResolutionStrategy : EvaluationStrategy {
  override val filters: Map<String, EvaluationFilter> = mapOf()

  class Serializer : StrategySerializer<ConflictResolutionStrategy> {
    override fun serialize(src: ConflictResolutionStrategy, typeOfSrc: Type, context: JsonSerializationContext): JsonObject = JsonObject()
    override fun deserialize(map: Map<String, Any>, language: String): ConflictResolutionStrategy = ConflictResolutionStrategy()
  }
}