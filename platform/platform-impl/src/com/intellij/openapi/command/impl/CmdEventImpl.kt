// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.Command


internal class CmdEventImpl(
  private val event: CommandEvent,
  id: CommandId,
  meta: CommandMeta,
) : CmdEventBase(id, meta) {

  override fun project(): Project? {
    return event.project
  }

  override fun name(): @Command String? {
    return event.commandName
  }

  override fun groupId(): Any? {
    return event.commandGroupId
  }

  override fun confirmationPolicy(): UndoConfirmationPolicy {
    return event.undoConfirmationPolicy
  }

  override fun recordOriginalDocument(): Boolean {
    return event.shouldRecordActionForOriginalDocument()
  }

  override fun isTransparent(): Boolean {
    return false
  }
}
