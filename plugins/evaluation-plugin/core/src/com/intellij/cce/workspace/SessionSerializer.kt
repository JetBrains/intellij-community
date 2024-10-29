// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.intellij.cce.core.Session
import com.intellij.cce.core.Suggestion
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.workspace.info.FileSessionsInfo
import java.lang.reflect.Type

class SessionSerializer {
  companion object {
    private val gson = GsonBuilder()
      .serializeNulls()
      .registerTypeAdapter(TokenProperties::class.java, TokenProperties.JsonAdapter)
      .create()
    private val gsonForJs = GsonBuilder()
      .serializeNulls()
      .registerTypeAdapter(TokenProperties::class.java, TokenProperties.JsonAdapter)
      .registerTypeAdapter(Suggestion::class.java, object : JsonSerializer<Suggestion> {
        override fun serialize(src: Suggestion, typeOfSrc: Type, context: JsonSerializationContext): JsonObject {
          val jsonObject = JsonObject()
          jsonObject.addProperty("text", src.text)
          jsonObject.addProperty("presentationText", src.presentationText)
          jsonObject.add("details", context.serialize(src.details))
          return jsonObject
        }
      })
      .create()
  }

  fun serialize(sessions: FileSessionsInfo): String = gson.toJson(sessions)

  fun serialize(sessions: List<Session>): String {
    val map = HashMap<String, Session>()
    for (session in sessions) {
      map[session.id] = session
    }
    return gsonForJs.toJson(map)
  }

  fun deserialize(json: String): FileSessionsInfo {
    return gson.fromJson(json, FileSessionsInfo::class.java)
  }
}