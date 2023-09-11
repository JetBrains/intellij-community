// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.ide.environment.impl.EnvironmentUtil
import com.intellij.ide.warmup.WarmupStatus
import com.intellij.idea.AppExitCodes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.text.Formats
import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl
import com.intellij.platform.util.ArgsParser
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistory
import com.intellij.util.indexing.diagnostic.ProjectIndexingActivityHistoryListener
import com.intellij.warmup.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.asDeferred
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.system.exitProcess

internal class ProjectCachesWarmup : ModernApplicationStarter() {
  override val commandName: String
    get() = "warmup"

  override fun premain(args: List<String>) {
    System.setProperty("ide.warmup.use.predicates", true.toString())
    if (System.getProperty("caches.indexerThreadsCount") == null) {
      System.setProperty("caches.indexerThreadsCount", max(1, Runtime.getRuntime().availableProcessors() - 1).toString())
    }
    //IDEA-241709
    System.setProperty("ide.browser.jcef.enabled", false.toString())
    //disable vcs log indexes
    System.setProperty("vcs.log.index.enable", false.toString())
    //disable slow edt access assertions
    System.setProperty("ide.slow.operations.assertion", false.toString())

    SystemProperties.setProperty("compile.parallel", true.toString())

    /*
     * An attempt to make warmup run close to usual UI run
     */
    SystemProperties.setProperty("ide.async.headless.mode", true.toString())

    // to avoid sync progress task execution as it works in test mode
    SystemProperties.setProperty("intellij.progress.task.ignoreHeadless", true.toString())

    SystemProperties.setProperty("intellij.MergingUpdateQueue.enable.global.flusher", true.toString())
  }

  override suspend fun start(args: List<String>) {
    val commandArgs = try {
      val parser = ArgsParser(args)
      val commandArgs = WarmupProjectArgsImpl(parser)
      parser.tryReadAll()
      if (commandArgs.build && commandArgs.rebuild) {
        throw InvalidWarmupArgumentsException("Only one of --build and --rebuild can be specified")
      }
      commandArgs
    }
    catch (t: Throwable) {
      val argsParser = ArgsParser(listOf())
      runCatching { WarmupProjectArgsImpl(argsParser) }
      ConsoleLog.error(
"""Failed to parse commandline: ${t.message}
  Usage:

  options:
    ${argsParser.usage(includeHidden = true)}""")
      exitProcess(2)
    }

    setEnvironmentConfiguration(commandArgs)

    configureVcsIndexing(commandArgs)

    val buildMode = getBuildMode(commandArgs)
    val builders = System.getenv()["IJ_WARMUP_BUILD_BUILDERS"]?.split(";")?.toHashSet()

    val application = ApplicationManager.getApplication()
    WarmupStatus.statusChanged(application, WarmupStatus.InProgress)
    val timeStart = System.currentTimeMillis()

    initLogger(args)

    val totalIndexedFiles = AtomicInteger(0)
    val handler = object : ProjectIndexingActivityHistoryListener {
      override fun onFinishedDumbIndexing(history: ProjectDumbIndexingHistory) {
        totalIndexedFiles.addAndGet(history.totalStatsPerFileType.values.sumOf { it.totalNumberOfFiles })
      }
    }
    application.messageBus.simpleConnect().subscribe(ProjectIndexingActivityHistoryListener.TOPIC, handler)

    val project = withLoggingProgresses {
      waitIndexInitialization()
      val project = try {
        importOrOpenProject(commandArgs)
      }
      catch(t: Throwable) {
        WarmupLogger.logError("Failed to load project", t)
        return@withLoggingProgresses null
      }

      waitForCachesSupports(project)

      if (buildMode != null) {
        waitForBuilders(project, buildMode, builders)
      }
      project
    } ?: exitProcess(AppExitCodes.STARTUP_EXCEPTION)

    waitUntilProgressTasksAreFinishedOrFail()

    waitForRefreshQueue()

    withLoggingProgresses {
      ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project, save = true)
    }

    WarmupStatus.statusChanged(application, WarmupStatus.Finished(totalIndexedFiles.get()))
    val timeEnd = System.currentTimeMillis()

    WarmupLogger.logInfo("""IDE Warm-up finished.
 - Elapsed time: ${Formats.formatDuration(timeEnd - timeStart)}
 - Number of indexed files: $totalIndexedFiles. 
Exiting the application...""")

    withContext(Dispatchers.EDT) {
      blockingContext {
        application.exit(false, true, false)
      }
    }
  }
}

private fun configureVcsIndexing(commandArgs: WarmupProjectArgs) {
  System.setProperty("vcs.log.index.enable", commandArgs.indexGitLog.toString())
}

private class InvalidWarmupArgumentsException(errorMessage: String) : Exception(errorMessage)

private enum class BuildMode {
  BUILD,
  REBUILD
}

private fun getBuildMode(args: WarmupProjectArgs) : BuildMode? {
  return if (args.build) {
    BuildMode.BUILD
  } else if (args.rebuild) {
    BuildMode.REBUILD
  } else when (System.getenv("IJ_WARMUP_BUILD")) {
    null -> null
    "REBUILD" -> BuildMode.REBUILD
    else -> BuildMode.BUILD
  }
}

private suspend fun waitForCachesSupports(project: Project) {
  val projectIndexesWarmupSupports = ProjectIndexesWarmupSupport.EP_NAME.getExtensionList(project)
  WarmupLogger.logInfo("Waiting for all ProjectIndexesWarmupSupport[${projectIndexesWarmupSupports.size}]...")
  val futures = projectIndexesWarmupSupports.mapNotNull { support ->
    try {
      support.warmAdditionalIndexes().asDeferred()
    }
    catch (t: Throwable) {
      WarmupLogger.logError("Failed to warm additional indexes $support", t)
      null
    }
  }
  try {
    withLoggingProgresses {
      futures.awaitAll()
    }
  }
  catch (t: Throwable) {
    WarmupLogger.logError("An exception occurred while awaiting indexes warmup", t)
  }
  WarmupLogger.logInfo("All ProjectIndexesWarmupSupport.waitForCaches completed")
}

private fun setEnvironmentConfiguration(commandArgs : WarmupProjectArgs) {
  val pathToConfig = commandArgs.pathToConfigurationFile
  if (pathToConfig != null) {
    EnvironmentUtil.setPathToConfigurationFile(pathToConfig.toAbsolutePath())
  }
}

private suspend fun waitForBuilders(project: Project, rebuild: BuildMode, builders: Set<String>?) {
  fun isBuilderEnabled(id: String): Boolean = if (builders.isNullOrEmpty()) true else builders.contains(id)

  val projectBuildWarmupSupports = ProjectBuildWarmupSupport.EP_NAME.getExtensionList(project).filter { builder ->
    isBuilderEnabled(builder.getBuilderId())
  }
  WarmupLogger.logInfo("Starting additional project builders[${projectBuildWarmupSupports.size}] (rebuild=$rebuild)...")
  try {
    val statusFlow = withLoggingProgresses {
      flow {
        for (builder in projectBuildWarmupSupports) {
          try {
            WarmupLogger.logInfo("Starting builder $builder for id ${builder.getBuilderId()}")
            val status = builder.buildProjectWithStatus(rebuild == BuildMode.REBUILD)
            WarmupLogger.logInfo("Builder $builder finished with status: ${status.message}")
            emit(status)
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (e: Throwable) {
            WarmupLogger.logError("Failed to call builder $builder", e)
            emit(WarmupBuildStatus.Failure(e.stackTraceToString()))
          }
        }
      }
    }
    val statuses = statusFlow.toList()
    val commonStatus =
      statuses.find { it is WarmupBuildStatus.Failure }
      ?: statuses.firstOrNull()
    WarmupLogger.logInfo("All warmup builders completed")
    if (commonStatus != null) {
      WarmupBuildStatus.statusChanged(commonStatus)
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (t: Throwable) {
    WarmupBuildStatus.statusChanged(WarmupBuildStatus.Failure(t.stackTraceToString()))
    WarmupLogger.logError("An exception occurred while awaiting builders", t)
  }
}

private suspend fun waitForRefreshQueue() {
  runTaskAndLogTime("RefreshQueue") {
    while (RefreshQueueImpl.isRefreshInProgress()) {
      WarmupLogger.logInfo("RefreshQueue is in progress...")
      delay(500)
    }
  }
}