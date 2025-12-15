// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.CommandEvent
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
  fun addEditorProvider(provider: ForeignEditorProvider)
  fun editorProviders(): List<ForeignEditorProvider>

  companion object {
    @JvmStatic
    fun create(id: CommandId, commandEvent: CommandEvent): CmdEvent {
      return CmdEventImpl(id, commandEvent)
    }

    @JvmStatic
    fun createTransparent(id: CommandId, project: Project?): CmdEvent {
      return CmdEventTransparent(id, project)
    }

    @JvmStatic
    fun createNonUndoable(id: CommandId): CmdEvent {
      return CmdEventNonUndoable(id)
    }
  }
}
