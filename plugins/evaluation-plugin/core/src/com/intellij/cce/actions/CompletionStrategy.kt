package com.intellij.cce.actions

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.intellij.cce.filter.EvaluationFilter
import java.lang.reflect.Type

data class CompletionStrategy(val prefix: CompletionPrefix,
                              val context: CompletionContext,
                              val emulateUser: Boolean,
                              val completionGolf: CompletionGolfMode?,
                              val filters: Map<String, EvaluationFilter>)

sealed class CompletionPrefix(val emulateTyping: Boolean) {
  object NoPrefix : CompletionPrefix(false)
  class CapitalizePrefix(emulateTyping: Boolean) : CompletionPrefix(emulateTyping)
  class SimplePrefix(emulateTyping: Boolean, val n: Int) : CompletionPrefix(emulateTyping)
}

enum class CompletionType {
  BASIC,
  SMART,
  ML,
  FULL_LINE, // python only
  CLANGD // c++ only
}

enum class CompletionContext {
  ALL,
  PREVIOUS
}

enum class CompletionGolfMode {
  ALL,
  TOKENS
}

class CompletionStrategySerializer : JsonSerializer<CompletionStrategy> {
  override fun serialize(src: CompletionStrategy, typeOfSrc: Type, context: JsonSerializationContext): JsonObject {
    val jsonObject = JsonObject()
    jsonObject.addProperty("emulateUser", src.emulateUser)
    if (src.completionGolf != null) {
      jsonObject.addProperty("completionGolf", src.completionGolf.name)
    }
    jsonObject.addProperty("context", src.context.name)
    val prefixClassName = src.prefix.javaClass.name
    val prefixObject = JsonObject()
    prefixObject.add("name", JsonPrimitive(prefixClassName.substring(prefixClassName.indexOf('$') + 1)))
    jsonObject.add("prefix", prefixObject)
    if (src.prefix !is CompletionPrefix.NoPrefix) prefixObject.add("emulateTyping", JsonPrimitive(src.prefix.emulateTyping))
    if (src.prefix is CompletionPrefix.SimplePrefix) prefixObject.add("n", JsonPrimitive(src.prefix.n))
    val filtersObject = JsonObject()
    src.filters.forEach { id, filter -> filtersObject.add(id, filter.toJson()) }
    jsonObject.add("filters", filtersObject)
    return jsonObject
  }
}
