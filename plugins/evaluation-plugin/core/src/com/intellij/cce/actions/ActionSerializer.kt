// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.google.gson.GsonBuilder
import com.intellij.cce.core.TokenProperties

object ActionSerializer {
  private val gson = GsonBuilder()
    .registerTypeAdapter(Action::class.java, Action.JsonAdapter)
    .registerTypeAdapter(TokenProperties::class.java, TokenProperties.JsonAdapter)
    .setPrettyPrinting()
    .create()

  fun serialize(actions: List<Action>): String = gson.toJson(actions)

  fun deserialize(json: String): List<Action> = gson.fromJson(json, Array<Action>::class.java).toList()

  fun serializeFileActions(actions: FileActions): String = gson.toJson(actions)

  fun deserializeFileActions(json: String): FileActions = gson.fromJson(json, FileActions::class.java)

  private data class FakeFileActions(val sessionsCount: Int)

  fun getSessionsCount(json: String): Int {
    return gson.fromJson(json, FakeFileActions::class.java).sessionsCount
  }
}

object ActionArraySerializer {
  private val gson = GsonBuilder()
    .registerTypeAdapter(Action::class.java, Action.JsonAdapter)
    .registerTypeAdapter(TokenProperties::class.java, TokenProperties.JsonAdapter)
    .setPrettyPrinting()
    .create()

  fun serialize(actions: Array<FileActions>): String = gson.toJson(actions)

  fun deserialize(json: String): Array<FileActions> = gson.fromJson(json, Array<FileActions>::class.java)
}