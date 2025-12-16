// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.CommandId


internal class NoCommandMeta(private val id: CommandId) : UndoCommandMeta {

  override fun commandId(): CommandId {
    return id
  }

  override fun editorProviders(): List<ForeignEditorProvider> {
    throw UnsupportedOperationException()
  }
}
