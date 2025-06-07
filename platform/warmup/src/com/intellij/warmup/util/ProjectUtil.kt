// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.warmup.WarmupStatus
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.configuration.ConfigurationResult
import com.intellij.openapi.project.configuration.awaitCompleteProjectConfiguration
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.observation.Observation
import com.intellij.util.asSafely
import com.intellij.warmup.waitIndexInitialization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.minutes

@ApiStatus.Internal
suspend fun importOrOpenProjectAsync(args: OpenProjectArgs): Project {
  WarmupLogger.logInfo("Opening project from ${args.projectDir}...")
  // most of the sensible operations would run in the same thread
  return runTaskAndLogTime("open project") {
    importOrOpenProjectImpl0(args)
  }
}

private suspend fun importOrOpenProjectImpl0(args: OpenProjectArgs): Project {
  val currentStatus = WarmupStatus.currentStatus()
  WarmupStatus.statusChanged(WarmupStatus.InProgress)
  waitIndexInitialization()
  try {
    return configureProjectByActivities(args)
  } finally {
    WarmupStatus.statusChanged(currentStatus)
  }
}


private suspend fun configureProjectByActivities(args: OpenProjectArgs): Project {
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
      WarmupLogger.logInfo(buildString {
        appendLine("Currently awaited activities:")
        appendLine(Observation.dumpAwaitedActivitiesToString())
      })
    }
  }
}

internal fun isPredicateBasedWarmup() = Registry.`is`("ide.warmup.use.predicates")