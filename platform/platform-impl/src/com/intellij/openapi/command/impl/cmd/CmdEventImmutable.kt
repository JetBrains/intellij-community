// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd

import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.impl.CommandId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.Command


internal class CmdEventImmutable(
  private val id: CommandId,
  private val project: Project?,
  private val name: @Command String?,
  private val groupId: Any?,
  private val confirmationPolicy: UndoConfirmationPolicy,
  private val recordOriginator: Boolean,
  private val isForeign: Boolean,
  private val meta: CmdMeta,
) : CmdEvent {
  override fun id(): CommandId = id
  override fun project(): Project? = project
  override fun name(): @Command String? = name
  override fun groupId(): Any? = groupId
  override fun confirmationPolicy(): UndoConfirmationPolicy = confirmationPolicy
  override fun recordOriginalDocument(): Boolean = recordOriginator
  override fun isTransparent(): Boolean = false
  override fun isForeign(): Boolean = isForeign
  override fun meta(): CmdMeta = meta
  override fun withNameAndGroupId(name: String?, groupId: Any?): CmdEvent {
    throw UnsupportedOperationException("withNameAndGroupId")
  }
}
