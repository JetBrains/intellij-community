// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd

import com.intellij.openapi.command.impl.CommandId
import com.intellij.openapi.components.service


open class CmdIdService {
  private val idGenerator = MyCmdIdGenerator()
  private val history = CmdHistory()

  fun nextCommandId(isTransparent: Boolean): CommandId {
    return if (isTransparent) {
      idGenerator.nextTransparentId()
    } else {
      idGenerator.nextCommandId()
    }
  }

  fun currentCommandId(): CommandId? {
    return history.currentCommandId
  }

  fun previousCommandId(): CommandId? {
    return history.previousCommandId
  }

  fun register(commandId: CommandId) {
    history.add(commandId)
  }

  fun historyDump(): String {
    return history.toString()
  }

  protected open fun createCommandId(id: Long): CommandId {
    return CommandId.fromLong(id)
  }

  private fun createCommandIdImpl(id: Long): CommandId {
    val commandId = createCommandId(id)
    register(commandId)
    return commandId
  }

  private inner class MyCmdIdGenerator : CmdIdGenerator() {
    override fun createId(commandId: Long): CommandId {
      return createCommandIdImpl(commandId)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): CmdIdService = service()
  }
}
