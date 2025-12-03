// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.fileEditor.FileEditor
import java.util.concurrent.atomic.AtomicReference


private class UndoForeignCommandServiceImpl : UndoForeignCommandService {

  private val currentForeignRef = AtomicReference<Pair<FileEditor?, DocumentReference?>>()

  override fun beforeStartForeignCommand(fileEditor: FileEditor?, originator: DocumentReference?) {
    currentForeignRef.set(Pair(fileEditor, originator))
  }

  override fun startForeignCommand(commandId: CommandId) {
    CommandIdService.setForcedCommand(commandId)
  }

  override fun finishForeignCommand() {
    currentForeignRef.set(null)
    CommandIdService.setForcedCommand(null)
  }

  override fun isForeignIsProgress(): Boolean {
    return currentForeignRef.get() != null
  }

  override fun foreignFileEditor(): FileEditor? {
    return currentForeignRef.get().first
  }

  override fun foreignOriginator(): DocumentReference? {
    return currentForeignRef.get().second
  }
}
