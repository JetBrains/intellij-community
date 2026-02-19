// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd

import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.impl.CommandId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.Command


interface CmdEvent {
  fun id(): CommandId
  fun project(): Project?
  fun name(): @Command String?
  fun groupId(): Any?
  fun confirmationPolicy(): UndoConfirmationPolicy
  fun recordOriginalDocument(): Boolean
  fun isTransparent(): Boolean
  fun isForeign(): Boolean
  fun meta(): CmdMeta
  fun withNameAndGroupId(name: @Command String?, groupId: Any?): CmdEvent

  companion object {
    @JvmStatic
    fun create(event: CommandEvent, id: CommandId, meta: CmdMeta): CmdEvent {
      return CmdEventImpl(event, id, meta)
    }

    @JvmStatic
    fun createTransparent(id: CommandId, isForeign: Boolean, meta: CmdMeta): CmdEvent {
      return CmdEventTransparent(null, isForeign, id, meta)
    }

    @JvmStatic
    fun createNonUndoable(commandId: CommandId, meta: CmdMeta): CmdEvent {
      return CmdEventNonUndoable(commandId, meta)
    }

    @JvmStatic
    fun create(
      commandId: CommandId,
      commandProject: Project?,
      commandName: @Command String?,
      groupId: Any?,
      confirmationPolicy: UndoConfirmationPolicy,
      recordOriginator: Boolean,
      isForeign: Boolean,
      commandMeta: CmdMeta,
    ): CmdEvent {
      return CmdEventImmutable(
        commandId,
        commandProject,
        commandName,
        groupId,
        confirmationPolicy,
        recordOriginator,
        isForeign,
        commandMeta,
      )
    }
  }
}
