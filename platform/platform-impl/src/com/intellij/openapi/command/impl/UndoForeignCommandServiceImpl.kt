// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicReference


private class UndoForeignCommandServiceImpl : UndoForeignCommandService {

  private val currentForeignRef = AtomicReference<MutableMap<Project?, ForeignEditorProvider>>()

  override fun beforeStartForeignCommand(project: Project?, fileEditor: FileEditor?, originator: DocumentReference?) {
    val map = currentForeignRef.updateAndGet { prev ->
      prev ?: mutableMapOf()
    }
    map[project] = ForeignEditorProvider(fileEditor, originator)
  }

  override fun startForeignCommand(commandId: CommandId) {
    CommandIdService.setForcedCommand(commandId)
  }

  override fun finishForeignCommand() {
    currentForeignRef.set(null)
    CommandIdService.setForcedCommand(null)
  }

  override fun getForeignEditorProvider(project: Project?): ForeignEditorProvider? {
    return currentForeignRef.get()?.get(project)
  }
}
