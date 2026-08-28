// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL
import org.junit.jupiter.api.fail
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Collects the exceptions that end a thread during a test run.
 *
 * [Thread.setDefaultUncaughtExceptionHandler] is one slot for the whole JVM, so one handler serves
 * the whole engine run. [getOrInstall] puts the handler in place, and each test drains it.
 *
 * @see UncaughtExceptionExtension
 */
@TestOnly
internal class TestUncaughtExceptionHandler : Thread.UncaughtExceptionHandler {

  private val lock = Any()
  private val uncaughtExceptions = ArrayList<Throwable>()

  override fun uncaughtException(t: Thread, e: Throwable) {
    synchronized(lock) {
      uncaughtExceptions.add(e)
    }
  }

  fun drainExceptions(predicate: (Throwable) -> Boolean): List<Throwable> {
    return synchronized(lock) {
      val matching = ArrayList<Throwable>()
      val iterator = uncaughtExceptions.iterator()
      while (iterator.hasNext()) {
        val throwable = iterator.next()
        if (predicate(throwable)) {
          matching.add(throwable)
          iterator.remove()
        }
      }
      matching
    }
  }

  /**
   * Reports the exceptions that arrived since the previous call, and removes them.
   *
   * The removal is necessary. The handler outlives the test, so a report that keeps the exceptions
   * fails every later test with the same ones.
   */
  fun assertAllExceptionAreCaught() {
    val drained = synchronized(lock) {
      if (uncaughtExceptions.isEmpty()) return
      val snapshot = ArrayList(uncaughtExceptions)
      uncaughtExceptions.clear()
      snapshot
    }
    fail(describeUncaughtExceptions(drained))
  }

  companion object {

    /**
     * Returns the handler of the current engine run, and installs it on the first call.
     *
     * The handler goes into the store of the root context, so the run installs it one time and
     * restores the previous handler one time. A nested [org.junit.platform.launcher.Launcher] run
     * has its own root context, and therefore its own handler.
     */
    @TestOnly
    fun getOrInstall(context: ExtensionContext): TestUncaughtExceptionHandler {
      val installed = context.root.getStore(GLOBAL).getOrComputeIfAbsent(
        InstalledHandler::class.java,
        { InstalledHandler() },
        InstalledHandler::class.java,
      )
      return installed.handler
    }
  }
}

@TestOnly
private class InstalledHandler : AutoCloseable {

  private val previousHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

  val handler: TestUncaughtExceptionHandler = TestUncaughtExceptionHandler()

  init {
    Thread.setDefaultUncaughtExceptionHandler(handler)
  }

  override fun close() {
    Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    // The last test of the run drained the handler before this point. An exception that arrives
    // after that drain has no test to fail, so report it here instead of a silent drop.
    val late = handler.drainExceptions { true }
    if (late.isNotEmpty()) {
      System.err.println(describeUncaughtExceptions(late))
    }
  }
}

@TestOnly
private fun describeUncaughtExceptions(exceptions: List<Throwable>): String {
  val bytes = ByteArrayOutputStream()
  PrintStream(bytes).use { stream ->
    exceptions.forEachIndexed { index, throwable ->
      stream.println("${index + 1}) ")
      throwable.printStackTrace(stream)
    }
  }
  return "${exceptions.size} uncaught exceptions:${System.lineSeparator()}$bytes"
}
