// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.actions.DatasetContext
import com.intellij.cce.evaluation.step.SetupStatsCollectorStep
import com.intellij.cce.interpreter.InterpretFilter
import com.intellij.cce.interpreter.InterpretationHandlerImpl
import com.intellij.cce.util.ExceptionsUtil
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.cce.workspace.info.FileErrorInfo
import com.intellij.cce.workspace.info.FileSessionsInfo
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.cce.workspace.storages.LogsSaver
import com.intellij.cce.workspace.storages.asCompositeLogsSaver
import com.intellij.cce.workspace.storages.logsSaverIf
import com.intellij.openapi.diagnostic.Logger
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

class ActionsInterpretationHandler(
  private val config: Config,
  private val datasetContext: DatasetContext
) {

  companion object {
    val LOG = Logger.getInstance(ActionsInterpretationHandler::class.java)
  }

  private fun createLogsSaver(workspace: EvaluationWorkspace): LogsSaver = listOf(
    logsSaverIf(config.interpret.saveLogs) { workspace.statLogsSaver },
    logsSaverIf(config.interpret.saveFusLogs) { workspace.fusLogsSaver }
  ).asCompositeLogsSaver()

  fun invoke(environment: EvaluationEnvironment, workspace: EvaluationWorkspace, indicator: Progress) {
    var sessionsCount: Int
    val computingTime = measureTimeMillis {
      sessionsCount = environment.sessionCount(datasetContext)
    }
    LOG.info("Computing of sessions count took $computingTime ms")
    val interpretationConfig = config.interpret
    val logsSaver = createLogsSaver(workspace)
    val handler = InterpretationHandlerImpl(indicator, sessionsCount, interpretationConfig.sessionsLimit)
    val filter =
      if (interpretationConfig.sessionProbability < 1)
        RandomInterpretFilter(interpretationConfig.sessionProbability, interpretationConfig.sessionSeed)
      else InterpretFilter.default()
    val featuresStorage = if (interpretationConfig.saveFeatures) workspace.featuresStorage else FeaturesStorage.EMPTY
    LOG.info("Start interpreting actions")
    if (interpretationConfig.sessionProbability < 1) {
      val skippedSessions = (sessionsCount * (1.0 - interpretationConfig.sessionProbability)).roundToInt()
      println("During actions interpretation will be skipped about $skippedSessions sessions")
    }
    var fileCount = 0
    for (chunk in environment.chunks(datasetContext)) {
      val iterations = interpretationConfig.iterationCount ?: 1
      for (iteration in 1..iterations) {
        if (config.interpret.filesLimit?.let { it <= fileCount } == true) {
          break
        }

        val iterationLabel = if (interpretationConfig.iterationCount != null) "Iteration $iteration" else ""
        val chunkName = if (iterationLabel.isNotEmpty()) "${chunk.name} - $iterationLabel" else chunk.name

        workspace.fullLineLogsStorage.enableLogging(chunkName)

        try {
          val result = logsSaver.invokeRememberingLogs {
            chunk.evaluate(handler, filter, interpretationConfig.order) { session ->
              featuresStorage.saveSession(session, chunkName)
            }
          }

          if (result.sessions.isNotEmpty()) {
            val sessionsInfo = FileSessionsInfo(
              projectName = chunk.datasetName,
              filePath = chunkName,
              text = result.presentationText ?: "",
              sessions = result.sessions
            )
            workspace.sessionsStorage.saveSessions(sessionsInfo)
            fileCount += 1
          }
          else {
            if (chunk.sessionsExist) {
              LOG.warn("No sessions collected from file: $chunkName")
            }
          }
        }
        catch (e: StopEvaluationException) {
          throw e
        }
        catch (e: Throwable) {
          try {
            workspace.errorsStorage.saveError(
              FileErrorInfo(chunkName, e.message ?: "No Message", ExceptionsUtil.stackTraceToString(e))
            )
          }
          catch (e2: Throwable) {
            LOG.error("Exception on saving error info.", e2)
          }
          handler.onErrorOccurred(e, 0)
        }
      }
      if (handler.isCancelled() || handler.isLimitExceeded()) {
        break
      }
    }
    logsSaver.save(config.actions?.language, config.interpret.trainTestSplit)
    SetupStatsCollectorStep.deleteLogs()
    workspace.saveMetadata()
    LOG.info("Interpreting actions completed")
  }
}
