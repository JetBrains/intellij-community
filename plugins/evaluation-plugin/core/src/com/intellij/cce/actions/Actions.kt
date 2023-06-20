// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.google.gson.*
import com.intellij.cce.core.TokenProperties
import java.lang.reflect.Type

sealed class Action(val type: ActionType) {
  enum class ActionType {
    MOVE_CARET, CALL_FEATURE, PRINT_TEXT, DELETE_RANGE, RENAME
  }

  object JsonAdapter : JsonDeserializer<Action>, JsonSerializer<Action> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Action {
      return when (ActionType.valueOf(json.asJsonObject.get("type").asString)) {
        ActionType.MOVE_CARET -> context.deserialize(json, MoveCaret::class.java)
        ActionType.CALL_FEATURE -> context.deserialize(json, CallFeature::class.java)
        ActionType.PRINT_TEXT -> context.deserialize(json, PrintText::class.java)
        ActionType.DELETE_RANGE -> context.deserialize(json, DeleteRange::class.java)
        ActionType.RENAME -> context.deserialize(json, Rename::class.java)
      }
    }

    override fun serialize(src: Action, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
      return context.serialize(src)
    }
  }
}

data class FileActions(val path: String, val checksum: String, val sessionsCount: Int, val actions: List<Action>)

data class MoveCaret(val offset: Int) : Action(ActionType.MOVE_CARET)

data class Rename(val offset: Int, val newName: String) : Action(ActionType.RENAME)

data class CallFeature(val expectedText: String, val offset: Int, val nodeProperties: TokenProperties) : Action(
  ActionType.CALL_FEATURE)

data class PrintText(val text: String) : Action(ActionType.PRINT_TEXT)

data class DeleteRange(val begin: Int, val end: Int) : Action(ActionType.DELETE_RANGE)

data class TextRange(val start: Int, val end: Int)
