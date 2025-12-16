// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.CommandId
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.util.NlsContexts.Command


internal abstract class CmdEventBase(private val meta: CommandMeta) : CmdEvent {

  override fun id(): CommandId {
    return meta.commandId()
  }

  override fun name(): @Command String? {
    return ""
  }

  override fun groupId(): Any? {
    return null
  }

  override fun confirmationPolicy(): UndoConfirmationPolicy {
    return UndoConfirmationPolicy.DEFAULT
  }

  override fun recordOriginalDocument(): Boolean {
    return false
  }

  override fun meta(): CommandMeta {
    return meta
  }
}
