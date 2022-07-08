// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl
import com.intellij.platform.util.ArgsParser
import com.intellij.util.SystemProperties
import com.intellij.util.application
import com.intellij.warmup.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.system.exitProcess

internal class ProjectIndexesWarmup : ApplicationStarter {
  override val commandName: String
    get() = "warmup"

  override val requiredModality: Int
    get() = ApplicationStarter.NOT_IN_EDT

  override fun premain(args: List<String>) {
    if (System.getProperty("caches.indexerThreadsCount") == null) {
      System.setProperty("caches.indexerThreadsCount", max(1, Runtime.getRuntime().availableProcessors() - 1).toString())
    }
    //IDEA-241709
    System.setProperty("ide.browser.jcef.enabled", false.toString())
    //disable vcs log
    System.setProperty("vcs.log.index.git", false.toString())
    //disable slow edt access assertions
    System.setProperty("ide.slow.operations.assertion", false.toString())

    SystemProperties.setProperty("compile.parallel", true.toString())

    // to avoid sync progress task execution as it works in test mode
    SystemProperties.setProperty("intellij.progress.task.ignoreHeadless", true.toString())
  }

  override fun main(args: List<String>) {
    val commandArgs = try {
      val parser = ArgsParser(args)
      val commandArgs = OpenProjectArgsImpl(parser)
      parser.tryReadAll()
      commandArgs
    }
    catch (t: Throwable) {
      val argsParser = ArgsParser(listOf())
      runCatching { OpenProjectArgsImpl(argsParser) }
      ConsoleLog.error(
"""Failed to parse commandline: ${t.message}
  Usage:

  options:
    ${argsParser.usage(includeHidden = true)}""")
      exitProcess(2)
    }

    val buildMode = System.getenv()["IJ_WARMUP_BUILD"]
    val builders = System.getenv()["IJ_WARMUP_BUILD_BUILDERS"]?.split(";")?.toHashSet()

    val project = withLoggingProgresses {
      waitIndexInitialization()
      val project = try {
        importOrOpenProject(commandArgs, it)
      }
      catch(t: Throwable) {
        ConsoleLog.error("Failed to load project", t)
        return@withLoggingProgresses null
      }

      waitForCachesSupports(project)

      if (buildMode != null) {
        val rebuild = buildMode == "REBUILD"
        waitForBuilders(project, rebuild, builders)
      }
      project
    } ?: return

    waitUntilProgressTasksAreFinishedOrFail()

    waitForRefreshQueue()

    withLoggingProgresses {
      application.invokeAndWait {
        ProjectManager.getInstance().closeAndDispose(project)
      }
    }

    ConsoleLog.info("IDE Warm-up finished. Exiting the application...")
    application.invokeAndWait {
      ApplicationManager.getApplication().exit(false, true, false)
    }
  }
}

private fun waitForCachesSupports(project: Project) {
  val projectIndexesWarmupSupports = ProjectIndexesWarmupSupport.EP_NAME.getExtensions(project)
  ConsoleLog.info("Waiting for all ProjectIndexesWarmupSupport[${projectIndexesWarmupSupports.size}]...")
  val futures = projectIndexesWarmupSupports.mapNotNull { support ->
    try {
      support.warmAdditionalIndexes()
    }
    catch (t: Throwable) {
      ConsoleLog.error("Failed to warm additional indexes $support", t)
      null
    }
  }.toTypedArray()
  try {
    withLoggingProgresses {
      CompletableFuture.allOf(*futures).get()
    }
  }
  catch (t: Throwable) {
    ConsoleLog.error("An exception occurred while awaiting indexes warmup", t)
  }
  ConsoleLog.info("All ProjectIndexesWarmupSupport.waitForCaches completed")
}

private fun waitForBuilders(project: Project, rebuild: Boolean, builders: Set<String>?) {
  fun isBuilderEnabled(id: String): Boolean {
    if (builders.isNullOrEmpty())
      return true
    return builders.contains(id)
  }

  val projectBuildWarmupSupports = ProjectBuildWarmupSupport.EP_NAME.getExtensions(project).filter { builder ->
    isBuilderEnabled(builder.getBuilderId())
  }
  ConsoleLog.info("Starting additional project builders[${projectBuildWarmupSupports.size}] (rebuild=$rebuild)...")
  val buildFutures = projectBuildWarmupSupports.mapNotNull { builder ->
    ConsoleLog.info("Starting builder $builder for id ${builder.getBuilderId()}")
    try {
      builder.buildProject(rebuild)
    }
    catch (t: Throwable) {
      ConsoleLog.error("Failed to call builder $builder", t)
      null
    }
  }.toTypedArray()
  try {
    withLoggingProgresses {
      CompletableFuture.allOf(*buildFutures).get()
    }
  }
  catch (t: Throwable) {
    ConsoleLog.error("An exception occurred while awaiting builders", t)
  }
  ConsoleLog.info("All warmup builders completed")
}

private fun waitForRefreshQueue() {
  runBlocking {
    runTaskAndLogTime("RefreshQueue") {
      while (RefreshQueueImpl.isRefreshInProgress()) {
        ConsoleLog.info("RefreshQueue is in progress... ")
        delay(500)
      }
    }
  }
}