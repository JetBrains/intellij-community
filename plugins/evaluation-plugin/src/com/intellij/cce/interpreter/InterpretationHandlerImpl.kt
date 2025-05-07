// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.interpreter

import com.intellij.cce.actions.Action
import com.intellij.cce.actions.ActionStat
import com.intellij.cce.util.Progress
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.util.*

class InterpretationHandlerImpl(
  private val indicator: Progress,
  private val sessionsCount: Int,
  private val sessionsLimit: Int?,
  private val strictSessionsLimit: Boolean?,
) : InterpretationHandler {
  private companion object {
    val LOG = Logger.getInstance(InterpretationHandlerImpl::class.java)
  }

  private val minInMs = 60000
  private var completed = 0
  private val actionStats = mutableListOf<ActionStat>()

  override fun onActionStarted(action: Action) {
    actionStats.add(ActionStat(action, System.currentTimeMillis()))
  }

  override fun onSessionFinished(path: String, fileSessionsLeft: Int): Boolean {
    completed++
    updateProgress(path, fileSessionsLeft)
    return isCancelled()
  }

  override fun onFileProcessed(path: String) {
    LOG.info("Interpreting actions for file $path completed. Done: $completed/$sessionsCount")
  }

  override fun onErrorOccurred(error: Throwable, sessionsSkipped: Int) {
    completed += sessionsSkipped
    if (!ApplicationManager.getApplication().isUnitTestMode) LOG.error("Actions interpretation error", error)
  }

  override fun isCancelled(): Boolean {
    if (indicator.isCanceled()) {
      LOG.info("Interpreting actions is canceled by user.")
      return true
    }
    return false
  }

  override fun isLimitExceeded(): Boolean {
    if (sessionsLimit != null && completed >= sessionsLimit) {
      LOG.info("Sessions limit exceeded. Interpretation will be stopped.")
      return true
    }
    return false
  }

  override fun isStrictLimitExceeded(): Boolean =
    if (strictSessionsLimit == true) isLimitExceeded() else false

  private fun updateProgress(path: String, fileSessionsLeft: Int) {
    val actualSessionsCount = sessionsLimit ?: sessionsCount
    val perMinute = actionStats.count { it.timestamp > Date().time - minInMs }
    val fileName = File(path).name
    val limitExceededPostfix = if (isLimitExceeded()) " - limit exceeded; $fileSessionsLeft sessions left in the last file" else ""
    indicator.setProgress(fileName, "$fileName ($completed/$actualSessionsCount sessions, $perMinute act/min)$limitExceededPostfix",
                          completed.toDouble() / actualSessionsCount)
  }
}
