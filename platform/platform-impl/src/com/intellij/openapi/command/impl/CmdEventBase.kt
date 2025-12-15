// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.util.NlsContexts.Command
import java.util.*


internal abstract class CmdEventBase(
  private val id: CommandId,
  protected val editorProviders: MutableList<ForeignEditorProvider> = Collections.synchronizedList(mutableListOf()),
) : CmdEvent {

  private val editorProvidersView = Collections.unmodifiableList(editorProviders)

  override fun id(): CommandId {
    return id
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

  override fun addEditorProvider(provider: ForeignEditorProvider) {
    editorProviders.add(provider)
  }

  override fun editorProviders(): List<ForeignEditorProvider> {
    return editorProvidersView
  }
}
