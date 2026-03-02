// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.prompt

object AgentPromptContextRendererIds {
  const val SNIPPET: String = "snippet"
  const val FILE: String = "file"
  const val SYMBOL: String = "symbol"
  const val PATHS: String = "paths"
}

object AgentPromptPayload {
  fun obj(vararg entries: Pair<String, AgentPromptPayloadValue>): AgentPromptPayloadValue.Obj {
    return AgentPromptPayloadValue.Obj(linkedMapOf(*entries))
  }

  fun arr(vararg items: AgentPromptPayloadValue): AgentPromptPayloadValue.Arr {
    return AgentPromptPayloadValue.Arr(items.toList())
  }

  fun str(value: String): AgentPromptPayloadValue.Str = AgentPromptPayloadValue.Str(value)

  fun num(value: Int): AgentPromptPayloadValue.Num = AgentPromptPayloadValue.Num(value.toString())

  fun bool(value: Boolean): AgentPromptPayloadValue.Bool = AgentPromptPayloadValue.Bool(value)
}

fun AgentPromptPayloadValue.objOrNull(): AgentPromptPayloadValue.Obj? {
  return this as? AgentPromptPayloadValue.Obj
}

fun AgentPromptPayloadValue.Obj.string(name: String): String? {
  return (fields[name] as? AgentPromptPayloadValue.Str)?.value
}

fun AgentPromptPayloadValue.Obj.bool(name: String): Boolean? {
  return (fields[name] as? AgentPromptPayloadValue.Bool)?.value
}

fun AgentPromptPayloadValue.Obj.number(name: String): String? {
  return (fields[name] as? AgentPromptPayloadValue.Num)?.value
}

fun AgentPromptPayloadValue.Obj.array(name: String): List<AgentPromptPayloadValue>? {
  return (fields[name] as? AgentPromptPayloadValue.Arr)?.items
}

