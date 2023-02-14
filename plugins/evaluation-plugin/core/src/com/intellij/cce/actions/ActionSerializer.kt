package com.intellij.cce.actions

import com.google.gson.GsonBuilder
import com.intellij.cce.core.TokenProperties

object ActionSerializer {
  private val gson = GsonBuilder()
    .registerTypeAdapter(Action::class.java, Action.JsonAdapter)
    .registerTypeAdapter(TokenProperties::class.java, TokenProperties.JsonAdapter)
    .create()

  fun serialize(actions: FileActions): String = gson.toJson(actions)

  fun deserialize(json: String): FileActions = gson.fromJson(json, FileActions::class.java)

  private data class FakeFileActions(val sessionsCount: Int)

  fun getSessionsCount(json: String): Int {
    return gson.fromJson(json, FakeFileActions::class.java).sessionsCount
  }
}