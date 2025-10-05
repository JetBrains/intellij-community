// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.google.gson.*
import com.intellij.cce.core.DEFAULT_LOOKUP_ELEMENT_TYPE
import com.intellij.cce.core.TokenProperties
import java.lang.reflect.Type
import java.util.*

sealed interface Action {
  val type: ActionType
  val sessionId: SessionId

  enum class ActionType {
    MOVE_CARET, CALL_FEATURE, PRINT_TEXT, DELETE_RANGE, SELECT_RANGE, RENAME, DELAY, OPEN_FILE_IN_BACKGROUND, OPTIMISE_IMPORTS,
    ROLLBACK
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
        ActionType.OPEN_FILE_IN_BACKGROUND -> context.deserialize(json, OpenFileInBackground::class.java)
        ActionType.OPTIMISE_IMPORTS -> context.deserialize(json, OptimiseImports::class.java)
        ActionType.ROLLBACK -> context.deserialize(json, Rollback::class.java)
      }
    }

    override fun serialize(src: Action, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
      return context.serialize(src)
    }
  }
}

data class FileActions(val path: String, val checksum: String?, val sessionsCount: Int, val actions: List<Action>)

data class MoveCaret internal constructor(override val sessionId: SessionId, val offset: Int) : Action {
  override val type = Action.ActionType.MOVE_CARET

  internal constructor(sessionId: UUID, offset: Int) : this(UUIDBasedSessionId(sessionId), offset)
}

data class Rename internal constructor(override val sessionId: SessionId, val offset: Int, val newName: String) : Action {
  override val type = Action.ActionType.RENAME

  internal constructor(sessionId: UUID, offset: Int, newName: String) : this(UUIDBasedSessionId(sessionId), offset, newName)
}

data class CallFeature internal constructor(override val sessionId: SessionId, val expectedText: String, val offset: Int, val nodeProperties: TokenProperties) : Action {
  override val type: Action.ActionType = Action.ActionType.CALL_FEATURE

  internal constructor(sessionId: UUID, expectedText: String, offset: Int, nodeProperties: TokenProperties) : this(UUIDBasedSessionId(sessionId), expectedText, offset, nodeProperties)
}

data class PrintText internal constructor(override val sessionId: SessionId, val text: String) : Action {
  override val type: Action.ActionType = Action.ActionType.PRINT_TEXT

  internal constructor(sessionId: UUID, text: String) : this(UUIDBasedSessionId(sessionId), text)
}

data class DeleteRange internal constructor(override val sessionId: SessionId, val begin: Int, val end: Int) : Action {
  override val type: Action.ActionType = Action.ActionType.DELETE_RANGE

  internal constructor(sessionId: UUID, begin: Int, end: Int) : this(UUIDBasedSessionId(sessionId), begin, end)
}

data class SelectRange internal constructor(override val sessionId: SessionId, val begin: Int, val end: Int) : Action {
  override val type: Action.ActionType = Action.ActionType.SELECT_RANGE

  internal constructor(sessionId: UUID, begin: Int, end: Int) : this(UUIDBasedSessionId(sessionId), begin, end)
}

data class Delay internal constructor(override val sessionId: SessionId, val seconds: Int) : Action {
  override val type: Action.ActionType = Action.ActionType.DELAY

  internal constructor(sessionId: UUID, seconds: Int) : this(UUIDBasedSessionId(sessionId), seconds)
}

data class OpenFileInBackground internal constructor(override val sessionId: SessionId, val file: String) : Action {
  override val type: Action.ActionType = Action.ActionType.OPEN_FILE_IN_BACKGROUND

  internal constructor(sessionId: UUID, file: String) : this(UUIDBasedSessionId(sessionId), file)
}

data class OptimiseImports internal constructor(override val sessionId: SessionId, val file: String) : Action {
  override val type: Action.ActionType = Action.ActionType.OPTIMISE_IMPORTS
}

data class Rollback internal constructor(override val sessionId: SessionId, val file: String) : Action {
  override val type: Action.ActionType = Action.ActionType.ROLLBACK
}

data class TextRange(val start: Int, val end: Int, val elementType: String = DEFAULT_LOOKUP_ELEMENT_TYPE)

class ActionsBuilder {
  private val actions: MutableList<Action> = mutableListOf()

  fun build(): List<Action> = actions.toList()

  fun session(init: SessionBuilder.() -> Unit) {
    actions.addAll(SessionBuilder().apply(init).build())
  }

  class SessionBuilder(
    private val sessionId: SessionId = UUIDBasedSessionId(UUID.randomUUID()),
    private val actions: MutableList<Action> = mutableListOf(),
  ) {

    fun build(): List<Action> = actions.toList()

    fun moveCaret(offset: Int) = actions.add(MoveCaret(sessionId, offset))
    fun rename(offset: Int, newName: String) = actions.add(Rename(sessionId, offset, newName))
    fun callFeature(expectedText: String, offset: Int, nodeProperties: TokenProperties) = actions.add(CallFeature(sessionId, expectedText, offset, nodeProperties))
    fun printText(text: String) = actions.add(PrintText(sessionId, text))
    fun deleteRange(begin: Int, end: Int) = actions.add(DeleteRange(sessionId, begin, end))
    fun selectRange(begin: Int, end: Int) = actions.add(SelectRange(sessionId, begin, end))
    fun delay(seconds: Int) = actions.add(Delay(sessionId, seconds))
    fun openFileInBackground(filePath: String) = actions.add(OpenFileInBackground(sessionId, filePath))
    fun optimiseImports(filePath: String) = actions.add(OptimiseImports(sessionId, filePath))
    fun rollback(filePath: String) = actions.add(Rollback(sessionId, filePath))
  }
}
