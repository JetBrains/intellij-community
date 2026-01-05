// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.CommandProcessorEx
import com.intellij.openapi.command.impl.cmd.CmdEvent
import com.intellij.openapi.command.impl.cmd.CmdIdService
import com.intellij.openapi.components.service
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


@ApiStatus.Experimental
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

  fun startCommand(cmdEvent: CmdEvent) {
    assertStartAllowed(cmdEvent)
    CmdIdService.getInstance().register(cmdEvent.id())
    currentCommand.set(cmdEvent)
    val token = if (cmdEvent.isTransparent()) {
      startPlatformTransparent()
    } else {
      startPlatformCommand(cmdEvent)
    }
    tokenToFinish.set(token)
  }

  fun finishCommand() {
    assertFinishAllowed()
    val token = tokenToFinish.getAndSet(null)
    try {
      checkNotNull(token) {
        "unexpected state: no token to finish"
      }
      token.close()
    } finally {
      currentCommand.set(null)
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
      error("failed to start foreign command with platform CommandProcessor")
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
      throw AlreadyDisposedException("cannot perform command in disposed project: $project")
    }
    require(currentCommand() == null) {
      "cannot perform foreign command during another foreign command"
    }
    val commandProcessor = commandProcessor()
    require(!commandProcessor.isCommandInProgress) {
      "cannot perform foreign command during domestic command"
    }
    require(!commandProcessor.isUndoTransparentActionInProgress()) {
      "cannot perform foreign command during domestic transparent action"
    }
  }

  private fun assertFinishAllowed() {
    ThreadingAssertions.assertEventDispatchThread()
    requireNotNull(currentCommand()) {
      "cannot finish foreign command without starting it"
    }
  }

  private fun commandProcessor(): CommandProcessorEx {
    return CommandProcessor.getInstance() as CommandProcessorEx
  }

  companion object {
    @JvmStatic
    fun getInstance(): ForeignCommandProcessor {
      val application = ApplicationManager.getApplication()
      return application.service()
    }
  }
}
