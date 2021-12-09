package com.intellij.warmup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.platform.util.ArgsParser
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.warmup.util.OpenProjectArgsImpl
import com.intellij.warmup.util.importOrOpenProject
import com.intellij.warmup.util.runTaskAndLogTime
import com.intellij.warmup.util.withLoggingProgresses
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import java.time.Duration
import kotlin.math.max
import kotlin.system.exitProcess

class ProjectIndexesWarmup : ApplicationStarter {
  override fun getCommandName(): String {
    return "warmup"
  }

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
  }

  override fun main(args: List<String>) {
    val commandArgs = try {
      val parser = ArgsParser(args)
      val commandArgs = OpenProjectArgsImpl(parser)
      parser.tryReadAll()
      commandArgs
    }
    catch (t: Throwable) {
      println()
      println("Failed to parse commandline: ${t.message}")
      println("  Usage:")
      println()
      println("  options:")
      val argsParser = ArgsParser(listOf())
      runCatching { OpenProjectArgsImpl(argsParser) }
      println(argsParser.usage(includeHidden = true))
      exitProcess(2)
    }

    withLoggingProgresses {
      (FileBasedIndex.getInstance() as FileBasedIndexEx).waitUntilIndicesAreInitialized()
      try {
        importOrOpenProject(commandArgs, it)
      }
      catch (t: Throwable) {
        println("Failed to load the project: ${t.message}")
      }
    }
    try {
      waitUntilProgressTasksAreFinished()
    } catch (e: IllegalStateException) {
      println(e.message)
      exitProcess(2)
    }
    ApplicationManager.getApplication().exit(false, true, false)
  }

  private fun waitUntilProgressTasksAreFinished() = runBlocking {
    runTaskAndLogTime("Awaiting for progress tasks") {
      val timeout = System.getProperty("ide.progress.tasks.awaiting.timeout.min", "60").toLongOrNull() ?: 60
      val startTime = System.currentTimeMillis()
      while (CoreProgressManager.getCurrentIndicators().isNotEmpty()) {
        if (System.currentTimeMillis() - startTime > Duration.ofMinutes(timeout).toMillis()) {
          val timeoutMessage = StringBuilder("Progress tasks awaiting timeout.\n")
          timeoutMessage.appendLine("Not finished tasks:")
          for (indicator in CoreProgressManager.getCurrentIndicators()) {
            timeoutMessage.appendLine("  - ${indicator.text}")
          }
          error(timeoutMessage)
        }
        delay(Duration.ofMillis(100))
      }
    }
  }

  override fun getRequiredModality(): Int {
    return ApplicationStarter.NOT_IN_EDT
  }
}