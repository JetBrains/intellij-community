// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.CommandProcessorEx
import com.intellij.openapi.command.impl.cmd.CmdEvent
import com.intellij.openapi.command.impl.cmd.CmdIdService
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


@ApiStatus.Internal
class ForeignCommandProcessor {
  private val currentCommand = AtomicReference<CmdEvent>()
  private val tokenToFinish = AtomicReference<AutoCloseable>()
  private val isUndoDisabled = AtomicBoolean()

  fun isUndoDisabled(): Boolean {
    return isUndoDisabled.get()
  }

  fun setUndoDisabled(value: Boolean) {
    isUndoDisabled.set(value)
  }

  fun currentCommand(): CmdEvent? {
    return currentCommand.get()
  }

  fun startCommand(cmdStartEvent: CmdEvent) {
    assertStartAllowed(cmdStartEvent)
    CmdIdService.getInstance().register(cmdStartEvent.id())
    currentCommand.set(cmdStartEvent)
    val token = if (cmdStartEvent.isTransparent()) {
      startPlatformTransparent()
    } else {
      startPlatformCommand(cmdStartEvent)
    }
    tokenToFinish.set(token)
  }

  fun finishCommand(cmdFinishEvent: CmdEvent) {
    assertFinishAllowed()
    val token = tokenToFinish.getAndSet(null)
    try {
      if (token == null) {
        throw ForeignCommandException("unexpected state: no command token to finish")
      }
      applyCmdMeta(cmdFinishEvent)
      currentCommand.set(cmdFinishEvent)
      token.close()
    } finally {
      currentCommand.set(null)
    }
  }

  private fun applyCmdMeta(cmdFinishEvent: CmdEvent) {
    for (undoMeta in cmdFinishEvent.meta().undoMeta()) {
      val undoProject = undoMeta.undoProject()
      val undoManager = undoManager(undoProject)
      for (actionMeta in undoMeta.undoableActions()) {
        val actionType = actionMeta.type()
        val affectedDocuments = actionMeta.affectedDocuments()
        val isGlobal = actionMeta.isGlobal()
        val undoableAction = UndoableActionType.getAction(actionType, affectedDocuments, isGlobal)
        undoManager.undoableActionPerformed(undoableAction)
      }
      if (undoMeta.isForcedGlobal()) {
        undoManager.markCurrentCommandAsGlobal()
      }
    }
  }

  private fun startPlatformCommand(cmdEvent: CmdEvent): AutoCloseable {
    val commandProcessor = commandProcessor()
    val commandToken = commandProcessor.startCommand(
      cmdEvent.project(),
      cmdEvent.name(),
      cmdEvent.groupId(),
      cmdEvent.confirmationPolicy(),
    )
    if (commandToken == null) {
      currentCommand.set(null)
      throw ForeignCommandException(
        "failed to start foreign command with platform CommandProcessor, " +
        "probably domestic command is already in progress ${commandProcessor.currentCommand}"
      )
    }
    return AutoCloseable {
      commandProcessor.finishCommand(commandToken, null)
    }
  }

  private fun startPlatformTransparent(): AutoCloseable {
    return commandProcessor().withUndoTransparentAction()
  }

  private fun assertStartAllowed(cmdEvent: CmdEvent) {
    ThreadingAssertions.assertEventDispatchThread()
    val project = cmdEvent.project()
    if (project != null && project.isDisposed()) {
      throw ForeignCommandException("cannot perform command in disposed project: $project")
    }
    if (currentCommand() != null) {
      throw ForeignCommandException(
        "cannot perform foreign command during another foreign command ${currentCommand()}"
      )
    }
    val commandProcessor = commandProcessor()
    if (commandProcessor.isCommandInProgress) {
      throw ForeignCommandException("cannot perform foreign command during domestic command ${commandProcessor.currentCommand}")
    }
    if (commandProcessor.isUndoTransparentActionInProgress()) {
      throw ForeignCommandException("cannot perform foreign command during domestic transparent action")
    }
  }

  private fun assertFinishAllowed() {
    ThreadingAssertions.assertEventDispatchThread()
    if (currentCommand() == null) {
      throw ForeignCommandException("cannot finish foreign command without starting it")
    }
  }

  private fun undoManager(project: Project?): UndoManagerImpl {
    val undoManager = if (project == null) {
      UndoManager.getGlobalInstance()
    } else {
      UndoManager.getInstance(project)
    }
    return undoManager as UndoManagerImpl
  }

  private fun commandProcessor(): CommandProcessorEx {
    return CommandProcessor.getInstance() as CommandProcessorEx
  }

  companion object {
    @JvmStatic
    fun getInstance(): ForeignCommandProcessor = service()
  }
}

private class ForeignCommandException(message: String) : RuntimeException(message)
