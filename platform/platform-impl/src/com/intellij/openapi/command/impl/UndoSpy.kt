// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.ide.impl.UndoRemoteBehaviorService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.impl.cmd.CmdEvent
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Advanced listener of [UndoManagerImpl].
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface UndoSpy {

  fun commandStarted(cmdStartEvent: CmdEvent)

  fun undoableActionAdded(undoProject: Project?, action: UndoableAction, type: UndoableActionType)

  fun commandFinished(cmdFinishEvent: CmdEvent)

  fun <T> withBlind(action: () -> T): T

  companion object {
    @JvmStatic
    fun getInstance(): UndoSpy? {
      return ProgressManager.getInstance().computeInNonCancelableSection<UndoSpy?, Exception> {
        if (UndoRemoteBehaviorService.isSpeculativeUndoEnabled()) {
          val application = ApplicationManager.getApplication()
          application?.serviceOrNull<UndoSpy>()
        } else {
          null
        }
      }
    }

    @JvmStatic
    fun <T> withBlindSpot(action: () -> T): T {
      val undoSpy = getInstance()
      return if (undoSpy == null) {
        action()
      } else {
        undoSpy.withBlind(action)
      }
    }
  }
}
