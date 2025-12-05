// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
@ApiStatus.Internal
open class UndoSpyImpl : UndoSpy {

  protected var isBlindSpot: Boolean = false

  final override fun commandBeforeStarted(undoProject: Project?, editor: FileEditor?, originator: DocumentReference?) {
    if (!isBlindSpot) {
      commandBeforeStarted0(undoProject, editor, originator)
    }
  }

  final override fun commandStarted(cmdEvent: CmdEvent) {
    if (!isBlindSpot) {
      commandStarted0(cmdEvent)
    }
  }

  final override fun undoableActionAdded(undoProject: Project?, action: UndoableAction, type: UndoableActionType) {
    if (!isBlindSpot) {
      undoableActionAdded0(undoProject, action, type)
    }
  }

  final override fun commandFinished(cmdEvent: CmdEvent) {
    if (!isBlindSpot) {
      commandFinished0(cmdEvent)
    }
  }

  final override fun undoRedoPerformed(project: Project?, editor: FileEditor?, isUndo: Boolean) {
    if (!isBlindSpot) {
      undoRedoPerformed0(project, editor, isUndo)
    }
  }

  override fun <T> withBlind(action: () -> T): T {
    val isBlind = isBlindSpot
    isBlindSpot = true
    try {
      return action()
    } finally {
      isBlindSpot = isBlind
    }
  }

  protected open fun commandBeforeStarted0(undoProject: Project?, editor: FileEditor?, originator: DocumentReference?) {
  }

  protected open fun commandStarted0(cmdEvent: CmdEvent) {
  }

  protected open fun undoableActionAdded0(undoProject: Project?, action: UndoableAction, type: UndoableActionType) {
  }

  protected open fun commandFinished0(cmdEvent: CmdEvent) {
  }

  protected open fun undoRedoPerformed0(project: Project?, editor: FileEditor?, isUndo: Boolean) {
  }
}
