// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.ide.impl.UndoRemoteBehaviorService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Advanced listener of [UndoManagerImpl].
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface UndoSpy {

  companion object {
    @JvmStatic
    fun getInstance(): UndoSpy? {
      return ProgressManager.getInstance().computeInNonCancelableSection<UndoSpy?, Exception> {
        if (UndoRemoteBehaviorService.isSpeculativeUndoEnabled()) {
          val application = ApplicationManager.getApplication()
          application?.service<UndoSpy>()
        } else {
          null
        }
      }
    }
  }

  fun commandStarted(cmdEvent: CmdEvent)

  fun undoableActionAdded(undoProject: Project?, action: UndoableAction, type: UndoableActionType)

  fun commandFinished(cmdEvent: CmdEvent)

  fun undoRedoPerformed(project: Project?, editor: FileEditor?, isUndo: Boolean)

  // TODO: sync FE commands instead of flush
  fun commandMergerFlushed(project: Project?)
}
