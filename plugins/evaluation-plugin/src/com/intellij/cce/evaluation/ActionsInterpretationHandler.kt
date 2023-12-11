// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.evaluation.step.SetupStatsCollectorStep
import com.intellij.cce.interpreter.InterpretFilter
import com.intellij.cce.interpreter.InterpretationHandlerImpl
import com.intellij.cce.interpreter.Interpreter
import com.intellij.cce.interpreter.InvokersFactory
import com.intellij.cce.util.ExceptionsUtil
import com.intellij.cce.util.FilesHelper
import com.intellij.cce.util.Progress
import com.intellij.cce.util.text
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.cce.workspace.info.FileErrorInfo
import com.intellij.cce.workspace.info.FileSessionsInfo
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

class ActionsInterpretationHandler(
  private val config: Config,
  private val language: String,
  private val invokersFactory: InvokersFactory,
  private val project: Project) : TwoWorkspaceHandler {
  companion object {
    val LOG = Logger.getInstance(ActionsInterpretationHandler::class.java)
  }

  override fun invoke(workspace1: EvaluationWorkspace, workspace2: EvaluationWorkspace, indicator: Progress) {
    var sessionsCount: Int
    val computingTime = measureTimeMillis {
      sessionsCount = workspace1.actionsStorage.computeSessionsCount()
    }
    LOG.info("Computing of sessions count took $computingTime ms")
    val interpretationConfig = config.interpret
    val handler = InterpretationHandlerImpl(indicator, sessionsCount, interpretationConfig.sessionsLimit)
    val filter =
      if (interpretationConfig.sessionProbability < 1)
        RandomInterpretFilter(interpretationConfig.sessionProbability, interpretationConfig.sessionSeed)
      else InterpretFilter.default()
    val interpreter = Interpreter(invokersFactory, handler, filter, config.interpret.order, project.basePath)
    val featuresStorage = if (interpretationConfig.saveFeatures) workspace2.featuresStorage else FeaturesStorage.EMPTY
    LOG.info("Start interpreting actions")
    if (interpretationConfig.sessionProbability < 1) {
      val skippedSessions = (sessionsCount * (1.0 - interpretationConfig.sessionProbability)).roundToInt()
      println("During actions interpretation will be skipped about $skippedSessions sessions")
    }
    val files = workspace1.actionsStorage.getActionFiles()
    for (file in files) {
      val fileActions = workspace1.actionsStorage.getActions(file)
      workspace2.fullLineLogsStorage.enableLogging(fileActions.path)
      try {
        val sessions = interpreter.interpret(fileActions) { session -> featuresStorage.saveSession(session, fileActions.path) }
        val fileText = FilesHelper.getFile(project, fileActions.path).text()
        workspace2.sessionsStorage.saveSessions(FileSessionsInfo(config.projectName, fileActions.path, fileText, sessions))
      }
      catch (e: Throwable) {
        try {
          workspace2.errorsStorage.saveError(
            FileErrorInfo(fileActions.path, e.message ?: "No Message", ExceptionsUtil.stackTraceToString(e))
          )
        }
        catch (e2: Throwable) {
          LOG.error("Exception on saving error info.", e2)
        }
        handler.onErrorOccurred(e, 0)
      }
      if (handler.isCancelled() || handler.isLimitExceeded()) break
    }
    if (interpretationConfig.saveLogs) workspace2.logsStorage.save(SetupStatsCollectorStep.statsCollectorLogsDirectory(), language, interpretationConfig.trainTestSplit)
    SetupStatsCollectorStep.deleteLogs()
    workspace2.saveMetadata()
    LOG.info("Interpreting actions completed")
  }
}
