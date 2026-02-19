// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectConfigurationUtil")

package com.intellij.openapi.project.configuration

import com.intellij.configurationStore.StoreUtil.saveSettings
import com.intellij.configurationStore.saveProjectsAndApp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.Observation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select

/**
 * Suspends until all configuration activities in a project are finished.
 *
 * In contrast to [Observation.awaitConfiguration], additionally saves files,
 * so that after the end of this method there are no activities scheduled by the IDE.
 *
 * @return a result of project configuration. The result is [ConfigurationResult.Failure] when an unrecoverable error occurs during the time of awaiting.
 */
suspend fun Project.awaitCompleteProjectConfiguration(messageHandler: ((String) -> Unit)?) : ConfigurationResult {
  return coroutineScope {
    val message = awaitProjectConfigurationOrFail(this@awaitCompleteProjectConfiguration, messageHandler).await()
    if (message != null) {
      FailureResult(message)
    } else {
      SuccessResult
    }
  }
}

/**
 * Result of project configuration process
 *
 * [Failure] indicates a severe problem when the IDE cannot proceed, such as an error from a build system
 */
sealed interface ConfigurationResult {
  interface Success : ConfigurationResult
  interface Failure : ConfigurationResult {
    val message: String
  }
}

private data object SuccessResult : ConfigurationResult.Success
private data class FailureResult(override val message: String) : ConfigurationResult.Failure

private fun CoroutineScope.awaitProjectConfigurationOrFail(project : Project, messageHandler: ((String) -> Unit)?) : Deferred<String?> {
  val abortDeferred = getFailureDeferred()
  val deferredConfiguration = getConfigurationDeferred(project, messageHandler)

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

private fun CoroutineScope.getConfigurationDeferred(project: Project, callback: ((String) -> Unit)?) : Deferred<Unit> {
  // we perform several phases of awaiting here,
  // because we need to be prepared for idempotent side effects from saving
  return async {
    // Ideally, we should do `while (true)` here to converge the process of configuration.
    // However, some clients (maven) schedule MergingUpdateQueue updates during save, which leads to infinite configuration,
    // as we think that MUQ updates may modify the state.
    // So instead we just invoke the save process at least 3 times, hoping that everyone manages to finalize their state by this moment
    repeat(3) { phaseNum ->
      val wasModified = Observation.awaitConfiguration(project, callback)
      if (wasModified) {
        saveSettings(componentManager = ApplicationManager.getApplication(), forceSavingAllSettings = true)
        saveProjectsAndApp(forceSavingAllSettings = true, onlyProject = project)
        callback?.invoke("Configuration phase $phaseNum is completed. Initiating another phase to cover possible side effects...") // NON-NLS
      }
      else {
        return@repeat
      }
      callback?.invoke("All configuration phases are completed.") // NON-NLS
    }
  }
}

private fun CoroutineScope.getFailureDeferred() : Deferred<String> {
  return async {
    val firstFatal = HeadlessLogging.loggingFlow().first { (level, _) -> level == HeadlessLogging.SeverityKind.Fatal }
    firstFatal.message.representation()
  }
}