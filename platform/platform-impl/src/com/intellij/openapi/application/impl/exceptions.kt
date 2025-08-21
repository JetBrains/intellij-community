// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.currentThreadContextOrNull
import com.intellij.diagnostic.IdeErrorsDialog
import com.intellij.diagnostic.LogMessage
import com.intellij.diagnostic.MessagePool
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionContextElement
import com.intellij.openapi.application.Interactive
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.Nls
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext


private val LOG: Logger = fileLogger()

internal fun processUnhandledException(
  exception: Throwable,
  coroutineContext: CoroutineContext?,
) {
  val coroutineContext = coroutineContext ?: currentThreadContextOrNull()
  val message = "Unhandled exception in ${coroutineContext?.toString() ?: "EDT"}"

  when (val interactiveMode = interactiveMode(coroutineContext)) {
    is Mode.Interactive -> {
      SwingUtilities.invokeLater {
        logExceptionSafely(message, exception, interactive = true)
        val defaultMessage = LogMessage(exception, message, emptyList())
        // "clear" button doesn't play well with interactive message. Once cleared, windows becomes empty.
        if (Registry.get("ide.exceptions.show.interactive").asBoolean()) {
          IdeErrorsDialog(MessagePool.getInstance(), null, false, defaultMessage, isModal = true, actionLeadToError = interactiveMode.action, hideClearButton = true).show()
        }
      }
    }
    Mode.NonInteractive -> {
      logExceptionSafely(message, exception, interactive = false)
    }
  }
}

private fun logExceptionSafely(message: String, exception: Throwable, interactive: Boolean) {
  try {
    LOG.error("$message, interactive mode: $interactive", exception)
  }
  catch (_: Throwable) {
  }
}

/**
 * Is exception was thrown as a part of interactive activity and must be displayed directly to user
 */
private fun interactiveMode(coroutineContext: CoroutineContext?): Mode {

  val action = coroutineContext?.get(ActionContextElement)
  val interactive = coroutineContext?.get(Interactive.Key)


  // interactive mode set explicitly
  if (interactive != null) {
    return Mode.Interactive(interactive.action)
  }
  else if (action != null) {
    val text = ActionManager.getInstance().getAction(action.actionId)?.templatePresentation?.text
    return Mode.Interactive(action = text)
  }
  // Exception thrown on EDT with modal dialog (or no project) has something to do with current user task
  if ((SwingUtilities.isEventDispatchThread() && LaterInvocator.isInModalContext()) ||
      ProjectManager.getInstanceIfCreated()?.openProjects?.isEmpty() == true) {
    return Mode.Interactive(action = null)
  }
  else {
    return Mode.NonInteractive
  }
}

private sealed interface Mode {
  data object NonInteractive : Mode
  data class Interactive(val action: @Nls String?) : Mode
}