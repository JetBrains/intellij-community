// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.CommandId
import java.util.Collections


internal class DomesticCommandMeta(private val id: CommandId) : UndoCommandMeta {
  private val editorProviders: MutableList<ForeignEditorProvider> = Collections.synchronizedList(mutableListOf())
  private val editorProvidersView = Collections.unmodifiableList(editorProviders)

  override fun commandId(): CommandId {
    return id
  }

  override fun editorProviders(): List<ForeignEditorProvider> {
    return editorProvidersView
  }

  fun addEditorProvider(provider: ForeignEditorProvider) {
    val found = editorProviders.find { it.undoProject === provider.undoProject }
    if (found != null) {
      error("editor provider for project already added ${found.undoProject()}")
    }
    editorProviders.add(provider)
  }
}
