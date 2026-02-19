// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.interpreter

import com.intellij.cce.actions.Action

interface InterpretationHandler {
  fun onActionStarted(action: Action)
  fun onSessionFinished(path: String, fileSessionsLeft: Int): Boolean
  fun onFileProcessed(path: String)
  fun onErrorOccurred(error: Throwable, sessionsSkipped: Int)
  fun isCancelled(): Boolean
  fun isLimitExceeded(): Boolean
  fun isStrictLimitExceeded(): Boolean
}