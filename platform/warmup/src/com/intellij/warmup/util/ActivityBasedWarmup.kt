// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.configuration.ConfigurationResult
import com.intellij.openapi.project.configuration.awaitCompleteProjectConfiguration
import com.intellij.platform.backend.observation.Observation
import com.intellij.util.asSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

internal suspend fun configureProjectByActivities(args: OpenProjectArgs): Project {
  val projectFile = getProjectFile(args)

  callProjectConversion(args)

  val project = runTaskAndLogTime("open project") {
    ProjectUtil.openOrImportAsync(projectFile.toNioPath(), OpenProjectTask())
  } ?: throw RuntimeException("Failed to open project, null is returned")

  val configurationError = runTaskAndLogTime("awaiting completion predicates") {
    val loggerJob = launchActivityLogger()
    val result = project.awaitCompleteProjectConfiguration(WarmupLogger::logInfo)
    loggerJob.cancel()
    dumpThreadsAfterConfiguration()
    result.asSafely<ConfigurationResult.Failure>()?.message
  }
  if (configurationError != null) {
    WarmupLogger.logError("Project configuration has failed: $configurationError")
    throw RuntimeException(configurationError)
  }

  checkProjectRoots(project)

  WarmupLogger.logInfo("Project is ready for the import")
  return project
}

private fun CoroutineScope.launchActivityLogger(): Job {
  return launch {
    while (true) {
      delay(10.minutes)
      val currentComputations = Observation.getAllAwaitedActivities()
      buildString {
        appendLine("Currently awaited activities:")
        for (trace in currentComputations) {
          appendLine(trace.stackTraceToString())
        }
      }
      WarmupLogger.logInfo(currentComputations.toString())
    }
  }
}