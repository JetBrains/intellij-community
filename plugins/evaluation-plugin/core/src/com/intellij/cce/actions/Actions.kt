package com.intellij.cce.actions

import com.google.gson.*
import com.intellij.cce.core.TokenProperties
import java.lang.reflect.Type

sealed class Action(val type: ActionType) {
  enum class ActionType {
    MOVE_CARET, CALL_COMPLETION, FINISH_SESSION, PRINT_TEXT, DELETE_RANGE, EMULATE_USER_SESSION, CODE_GOLF
  }

  object JsonAdapter : JsonDeserializer<Action>, JsonSerializer<Action> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Action {
      return when (ActionType.valueOf(json.asJsonObject.get("type").asString)) {
        ActionType.MOVE_CARET -> context.deserialize(json, MoveCaret::class.java)
        ActionType.CALL_COMPLETION -> context.deserialize(json, CallCompletion::class.java)
        ActionType.FINISH_SESSION -> context.deserialize(json, FinishSession::class.java)
        ActionType.PRINT_TEXT -> context.deserialize(json, PrintText::class.java)
        ActionType.DELETE_RANGE -> context.deserialize(json, DeleteRange::class.java)
        ActionType.EMULATE_USER_SESSION -> context.deserialize(json, EmulateUserSession::class.java)
        ActionType.CODE_GOLF -> context.deserialize(json, CompletionGolfSession::class.java)
      }
    }

    override fun serialize(src: Action, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
      return context.serialize(src)
    }
  }
}

data class FileActions(val path: String, val checksum: String, val sessionsCount: Int, val actions: List<Action>)

data class MoveCaret(val offset: Int) : Action(ActionType.MOVE_CARET)
data class CallCompletion(val prefix: String, val expectedText: String, val nodeProperties: TokenProperties) : Action(
  ActionType.CALL_COMPLETION)

class FinishSession : Action(ActionType.FINISH_SESSION)
data class PrintText(val text: String, val completable: Boolean = false) : Action(ActionType.PRINT_TEXT)
data class DeleteRange(val begin: Int, val end: Int, val completable: Boolean = false) : Action(ActionType.DELETE_RANGE)
data class EmulateUserSession(val expectedText: String, val nodeProperties: TokenProperties) : Action(ActionType.EMULATE_USER_SESSION)
data class CompletionGolfSession(val expectedText: String, val ranges: List<TextRange>) : Action(ActionType.CODE_GOLF)
data class TextRange(val start: Int, val end: Int)
