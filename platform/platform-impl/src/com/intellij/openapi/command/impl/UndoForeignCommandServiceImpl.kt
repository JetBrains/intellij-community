// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicReference


internal class UndoForeignCommandServiceImpl : UndoForeignCommandService {

  private val currentForeignRef = AtomicReference<MutableMap<Project?, ForeignEditorProvider>>()

  fun isCommandInProgress(): Boolean {
    return currentForeignRef.get() != null
  }

  override fun startForeignCommand(commandId: CommandId, editorProviders: List<ForeignEditorProvider>) {
    CommandIdService.setForcedCommand(commandId)
    val map: MutableMap<Project?, ForeignEditorProvider> = mutableMapOf()
    for (provider in editorProviders) {
      map[provider.undoProject()] = provider
    }
  }

  override fun finishForeignCommand() {
    currentForeignRef.set(null)
    CommandIdService.setForcedCommand(null)
  }

  override fun getForeignEditorProvider(project: Project?): ForeignEditorProvider? {
    return currentForeignRef.get()?.get(project)
  }
}
