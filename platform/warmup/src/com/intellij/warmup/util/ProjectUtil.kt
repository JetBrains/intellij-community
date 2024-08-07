// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.CommandLineProgressReporterElement
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.PatchProjectUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.warmup.WarmupConfigurator
import com.intellij.ide.warmup.WarmupStatus
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.progress.reportProgress
import com.intellij.util.asSafely
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.warmup.impl.WarmupConfiguratorOfCLIConfigurator
import com.intellij.warmup.impl.getCommandLineReporter
import com.intellij.warmup.waitIndexInitialization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.*

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
    return if (isPredicateBasedWarmup()) {
      configureProjectByActivities(args)
    } else {
      configureProjectByConfigurators(args)
    }
  } finally {
    WarmupStatus.statusChanged(currentStatus)
  }
}

private suspend fun configureProjectByConfigurators(args: OpenProjectArgs): Project {
  val projectFile = getProjectFile(args)

  yieldThroughInvokeLater()

  callProjectConversion(args)

  callProjectConfigurators(args) {
    this.prepareEnvironment(args.projectDir)
  }

  val project = runTaskAndLogTime("open project") {
    ProjectUtil.openOrImportAsync(projectFile.toNioPath(), OpenProjectTask())
  } ?: throw RuntimeException("Failed to open project, null is returned")
  yieldThroughInvokeLater()

  runTaskAndLogTime("patching project") {
    withContext(Dispatchers.EDT) {
      //TODO[jo]: allow to configure that from commandline parameters
      PatchProjectUtil.patchProject(project)
    }
  }


  callProjectConfigurators(args) {
    this.runWarmup(project)

    FileBasedIndex.getInstance().asSafely<FileBasedIndexImpl>()?.changedFilesCollector?.ensureUpToDate()
    //the configuration may add more dumb tasks to complete
    //we flush the queue to avoid a deadlock between a modal progress & invokeLater
    yieldAndWaitForDumbModeEnd(project)
  }

  WarmupLogger.logInfo("Project is ready for the import")
  return project
}


private suspend fun callProjectConfigurators(
  projectArgs: OpenProjectArgs,
  action: suspend WarmupConfigurator.() -> Unit
) {

  if (!projectArgs.configureProject) return

  val activeConfigurators = getAllConfigurators().filter {
    if (it.configuratorPresentableName in projectArgs.disabledConfigurators) {
      WarmupLogger.logInfo("Configurator ${it.configuratorPresentableName} is disabled in the settings")
      false
    } else {
      true
    }
  }

  withLoggingProgressReporter {
    reportProgress(activeConfigurators.size) { reporter ->
      for (configuration in activeConfigurators) {
        reporter.itemStep("Configurator ${configuration.configuratorPresentableName} is in action..." /* NON-NLS */) {
          runTaskAndLogTime("Configure " + configuration.configuratorPresentableName) {
            try {
              withContext(CommandLineProgressReporterElement(getCommandLineReporter(configuration.configuratorPresentableName))) {
                action(configuration)
              }
            } catch (e : CancellationException) {
              val message = (e.message ?: e.stackTraceToString()).lines().joinToString("\n") { "[${configuration.configuratorPresentableName}]: $it" }
              WarmupLogger.logInfo("Configurator '${configuration.configuratorPresentableName}' was cancelled with the following outcome:\n$message")
            }
          }
        }
      }
    }
  }

  yieldThroughInvokeLater()
}

private fun getAllConfigurators() : List<WarmupConfigurator> {
  val warmupConfigurators = WarmupConfigurator.EP_NAME.extensionList
  val nameSet = warmupConfigurators.mapTo(HashSet()) { it.configuratorPresentableName }
  return warmupConfigurators +
         CommandLineInspectionProjectConfigurator.EP_NAME.extensionList
           .filter {
             // Avoid qodana-specific configurators, we have our analogues for warmup
             it.name.startsWith("qodana").not() &&
             // New API should be preferable
             nameSet.contains(it.name).not() }
           .map(::WarmupConfiguratorOfCLIConfigurator)
}

internal fun isPredicateBasedWarmup() = Registry.`is`("ide.warmup.use.predicates")