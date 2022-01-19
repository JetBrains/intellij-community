// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.indexing.shared.ultimate.project.waitIndexInitialization
import com.intellij.indexing.shared.ultimate.project.waitUntilProgressTasksAreFinishedOrFail
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.platform.util.ArgsParser
import com.intellij.warmup.util.OpenProjectArgsImpl
import com.intellij.warmup.util.importOrOpenProject
import com.intellij.warmup.util.withLoggingProgresses
import kotlin.math.max
import kotlin.system.exitProcess

internal class ProjectIndexesWarmup : ApplicationStarter {
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
      waitIndexInitialization()
      try {
        importOrOpenProject(commandArgs, it)
      }
      catch (t: Throwable) {
        println("Failed to load the project: ${t.message}")
      }
    }
    waitUntilProgressTasksAreFinishedOrFail()
    println("IDE Warm-up finished. Exiting the application...")
    ApplicationManager.getApplication().exit(false, true, false)
  }

  override fun getRequiredModality(): Int {
    return ApplicationStarter.NOT_IN_EDT
  }
}