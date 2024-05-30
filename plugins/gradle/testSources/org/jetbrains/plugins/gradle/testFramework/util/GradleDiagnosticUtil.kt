// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.util.ThrowableComputable
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun <R> withThreadDumpEvery(delay: Duration, action: ThrowableComputable<R, Throwable>): R {
  val watcher = thread(name = "Thread dump watcher") {
    try {
      val startTime = System.currentTimeMillis()
      while (true) {
        Thread.sleep(delay.inWholeMilliseconds)
        val duration = System.currentTimeMillis() - startTime
        val threadDump = ThreadDumper.dumpThreadsToString()
        System.err.println(
          "The operation is still running for ${duration.milliseconds}\n" +
          "----------Thread dump---------\n" +
          threadDump + "\n" +
          "------------------------------\n"
        )
      }
    }
    catch (ignored: InterruptedException) {
    }
  }
  try {
    return action.compute()
  }
  finally {
    watcher.interrupt()
    watcher.join()
  }
}