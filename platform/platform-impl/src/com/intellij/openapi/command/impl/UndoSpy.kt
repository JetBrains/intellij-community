// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Advanced listener of [UndoManagerImpl].
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface UndoSpy {

  fun commandStarted(cmdEvent: CmdEvent)

  fun undoableActionAdded(
    project: Project?,
    action: UndoableAction,
    type: UndoableActionType,
  )

  fun commandFinished(cmdEvent: CmdEvent)

  fun undoRedoPerformed(
    project: Project?,
    editor: FileEditor?,
    isUndo: Boolean,
  )

  // TODO: sync FE commands instead of flush
  fun commandMergerFlushed(project: Project?)

  companion object {
    @JvmField
    val BLIND: UndoSpy = object : UndoSpy {
      override fun commandStarted(cmdEvent: CmdEvent) {}
      override fun undoableActionAdded(project: Project?, action: UndoableAction, type: UndoableActionType) {}
      override fun commandFinished(cmdEvent: CmdEvent) {}
      override fun undoRedoPerformed(project: Project?, editor: FileEditor?, isUndo: Boolean) {}
      override fun commandMergerFlushed(project: Project?) {}
    }
  }
}
