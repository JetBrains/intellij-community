// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.configuration.HeadlessLogging
import com.intellij.openapi.project.configuration.awaitCompleteProjectConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select

suspend fun configureProjectByActivities(args: OpenProjectArgs) : Project {
  val projectFile = getProjectFile(args)

  callProjectConversion(args)

  val project = runTaskAndLogTime("open project") {
    ProjectUtil.openOrImportAsync(projectFile.toNioPath(), OpenProjectTask())
  } ?: throw RuntimeException("Failed to open project, null is returned")

  val configurationError = runTaskAndLogTime("awaiting completion predicates") {
    val configurationError = awaitProjectConfigurationOrFail(project).await()
    dumpThreadsAfterConfiguration()
    configurationError
  }
  if (configurationError != null) {
    WarmupLogger.logError("Project configuration has failed: $configurationError")
    throw RuntimeException(configurationError)
  }

  checkProjectRoots(project)

  WarmupLogger.logInfo("Project is ready for the import")
  return project
}

private fun CoroutineScope.getFailureDeferred() : Deferred<String> {
  return async {
    val firstFatal = HeadlessLogging.loggingFlow().first { (level, _) -> level == HeadlessLogging.SeverityKind.Fatal }
    firstFatal.message.representation()
  }
}

private fun CoroutineScope.getConfigurationDeferred(project : Project) : Deferred<Unit> {
  return async {
    withLoggingProgressReporter {
      project.awaitCompleteProjectConfiguration(WarmupLogger::logInfo)
    }
  }
}

private fun CoroutineScope.awaitProjectConfigurationOrFail(project : Project) : Deferred<String?> {
  val abortDeferred = getFailureDeferred()
  val deferredConfiguration = getConfigurationDeferred(project)

  return async {
    select<String?> {
      deferredConfiguration.onAwait {
        abortDeferred.cancel()
        null
      }
      abortDeferred.onAwait { it ->
        deferredConfiguration.cancel()
        it
      }
    }
  }
}
