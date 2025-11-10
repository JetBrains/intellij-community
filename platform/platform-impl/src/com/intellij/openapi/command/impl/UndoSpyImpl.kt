// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
@ApiStatus.Internal
open class UndoSpyImpl : UndoSpy {

  override fun commandStarted(cmdEvent: CmdEvent) {
  }

  override fun undoableActionAdded(undoProject: Project?, action: UndoableAction, type: UndoableActionType) {
  }

  override fun commandFinished(cmdEvent: CmdEvent) {
  }

  override fun undoRedoPerformed(project: Project?, editor: FileEditor?, isUndo: Boolean) {
  }

  override fun commandMergerFlushed(project: Project?) {
  }
}
