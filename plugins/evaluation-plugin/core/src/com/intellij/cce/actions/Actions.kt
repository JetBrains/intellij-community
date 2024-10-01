// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.google.gson.*
import com.intellij.cce.core.TokenProperties
import java.lang.reflect.Type
import java.util.*

sealed interface Action {
  val type: ActionType
  val sessionId: UUID

  enum class ActionType {
    MOVE_CARET, CALL_FEATURE, PRINT_TEXT, DELETE_RANGE, SELECT_RANGE, RENAME, DELAY
  }

  object JsonAdapter : JsonDeserializer<Action>, JsonSerializer<Action> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Action {
      return when (ActionType.valueOf(json.asJsonObject.get("type").asString)) {
        ActionType.MOVE_CARET -> context.deserialize(json, MoveCaret::class.java)
        ActionType.CALL_FEATURE -> context.deserialize(json, CallFeature::class.java)
        ActionType.PRINT_TEXT -> context.deserialize(json, PrintText::class.java)
        ActionType.DELETE_RANGE -> context.deserialize(json, DeleteRange::class.java)
        ActionType.SELECT_RANGE -> context.deserialize(json, SelectRange::class.java)
        ActionType.RENAME -> context.deserialize(json, Rename::class.java)
        ActionType.DELAY -> context.deserialize(json, Delay::class.java)
      }
    }

    override fun serialize(src: Action, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
      return context.serialize(src)
    }
  }
}

data class FileActions(val path: String, val checksum: String, val sessionsCount: Int, val actions: List<Action>)

data class MoveCaret internal constructor(override val sessionId: UUID, val offset: Int) : Action {
  override val type = Action.ActionType.MOVE_CARET
}

data class Rename internal constructor(override val sessionId: UUID, val offset: Int, val newName: String) : Action {
  override val type = Action.ActionType.RENAME
}

data class CallFeature internal constructor(override val sessionId: UUID, val expectedText: String, val offset: Int, val nodeProperties: TokenProperties) : Action {
  override val type: Action.ActionType = Action.ActionType.CALL_FEATURE
}

data class PrintText internal constructor(override val sessionId: UUID, val text: String) : Action {
  override val type: Action.ActionType = Action.ActionType.PRINT_TEXT
}

data class DeleteRange internal constructor(override val sessionId: UUID, val begin: Int, val end: Int) : Action {
  override val type: Action.ActionType = Action.ActionType.DELETE_RANGE
}

data class SelectRange internal constructor(override val sessionId: UUID, val begin: Int, val end: Int) : Action {
  override val type: Action.ActionType = Action.ActionType.SELECT_RANGE
}

data class Delay internal constructor(override val sessionId: UUID, val seconds: Int) : Action {
  override val type: Action.ActionType = Action.ActionType.DELAY
}

data class TextRange(val start: Int, val end: Int)


class ActionsBuilder {
  private val actions: MutableList<Action> = mutableListOf()

  fun build(): List<Action> = actions.toList()

  fun session(init: SessionBuilder.() -> Unit) {
    actions.addAll(SessionBuilder().apply(init).build())
  }

  class SessionBuilder(
    private val sessionId: UUID = UUID.randomUUID(),
    private val actions: MutableList<Action> = mutableListOf()
  ) {

    fun build(): List<Action> = actions.toList()

    fun moveCaret(offset: Int) = actions.add(MoveCaret(sessionId, offset))
    fun rename(offset: Int, newName: String) = actions.add(Rename(sessionId, offset, newName))
    fun callFeature(expectedText: String, offset: Int, nodeProperties: TokenProperties) = actions.add(CallFeature(sessionId, expectedText, offset, nodeProperties))
    fun printText(text: String) = actions.add(PrintText(sessionId, text))
    fun deleteRange(begin: Int, end: Int) = actions.add(DeleteRange(sessionId, begin, end))
    fun selectRange(begin: Int, end: Int) = actions.add(SelectRange(sessionId, begin, end))
    fun delay(seconds: Int) = actions.add(Delay(sessionId, seconds))
  }
}
