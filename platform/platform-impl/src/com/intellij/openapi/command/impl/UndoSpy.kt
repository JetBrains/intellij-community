// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.Command
import org.jetbrains.annotations.ApiStatus

/**
 * Advanced listener of [UndoManagerImpl].
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface UndoSpy {

  fun commandStarted(
    project: Project?,
    undoConfirmationPolicy: UndoConfirmationPolicy,
  )

  fun addUndoableAction(
    action: UndoableAction,
    type: UndoableActionType,
  )

  fun commandFinished(
    project: Project?,
    commandName: @Command String?,
    commandGroupId: Any?,
    isTransparent: Boolean,
  )

  companion object {
    @JvmField
    val BLIND: UndoSpy = object : UndoSpy {
      override fun commandStarted(project: Project?, undoConfirmationPolicy: UndoConfirmationPolicy) = Unit
      override fun addUndoableAction(action: UndoableAction, type: UndoableActionType) = Unit
      override fun commandFinished(project: Project?, commandName: @Command String?, commandGroupId: Any?, isTransparent: Boolean) = Unit
    }
  }
}
