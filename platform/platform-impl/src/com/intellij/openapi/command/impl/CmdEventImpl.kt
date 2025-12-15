// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.Command


internal class CmdEventImpl(
  id: CommandId,
  private val commandEvent: CommandEvent,
) : CmdEventBase(id) {

  override fun project(): Project? {
    return commandEvent.project
  }

  override fun name(): @Command String? {
    return commandEvent.commandName
  }

  override fun groupId(): Any? {
    return commandEvent.commandGroupId
  }

  override fun confirmationPolicy(): UndoConfirmationPolicy {
    return commandEvent.undoConfirmationPolicy
  }

  override fun recordOriginalDocument(): Boolean {
    return commandEvent.shouldRecordActionForOriginalDocument()
  }

  override fun isTransparent(): Boolean {
    return false
  }
}
