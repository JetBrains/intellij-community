package com.intellij.cce.actions

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

class SessionIdJsonAdapter : JsonDeserializer<SessionId>, JsonSerializer<SessionId> {
  override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): SessionId? = when {
    json == null -> null
    json.isJsonPrimitive -> ExplicitSessionId(json.asString)
    else -> throw JsonParseException("Invalid SessionId format")
  }

  override fun serialize(src: SessionId?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? = when (src) {
    null -> null
    else -> context?.serialize(src.id)
  }
}