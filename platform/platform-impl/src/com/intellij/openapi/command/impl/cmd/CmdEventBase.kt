// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd

import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.impl.CommandId
import com.intellij.openapi.util.NlsContexts.Command


internal abstract class CmdEventBase(
  private val commandId: CommandId,
  private val meta: CmdMeta
) : CmdEvent {
  final override fun id(): CommandId = commandId
  final override fun meta(): CmdMeta = meta
  override fun name(): @Command String? = ""
  override fun groupId(): Any? = null
  override fun confirmationPolicy(): UndoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT
  override fun recordOriginalDocument(): Boolean = false
}
