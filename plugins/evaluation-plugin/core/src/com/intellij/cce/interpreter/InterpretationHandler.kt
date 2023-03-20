package com.intellij.cce.interpreter

import com.intellij.cce.actions.Action

interface InterpretationHandler {
  fun onActionStarted(action: Action)
  fun onSessionFinished(path: String): Boolean
  fun onFileProcessed(path: String)
  fun onErrorOccurred(error: Throwable, sessionsSkipped: Int)
  fun isCancelled(): Boolean
  fun isLimitExceeded(): Boolean
}