// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandId
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.Command
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal


@Experimental
@Internal
interface CmdEvent {
  fun id(): CommandId
  fun project(): Project?
  fun name(): @Command String?
  fun groupId(): Any?
  fun confirmationPolicy(): UndoConfirmationPolicy
  fun recordOriginalDocument(): Boolean
  fun isTransparent(): Boolean
  fun meta(): CommandMeta

  companion object {
    @JvmStatic
    fun create(event: CommandEvent, meta: CommandMeta): CmdEvent {
      return CmdEventImpl(event, meta)
    }

    @JvmStatic
    fun createTransparent(project: Project?, meta: CommandMeta): CmdEvent {
      return CmdEventTransparent(project, meta)
    }

    @JvmStatic
    fun createNonUndoable(meta: CommandMeta): CmdEvent {
      return CmdEventNonUndoable(meta)
    }
  }
}
