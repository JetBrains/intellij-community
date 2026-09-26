// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.impl.TestUncaughtExceptionHandler
import org.jetbrains.annotations.TestOnly

/**
 * Drains uncaught exceptions matching [predicate] from the [TestUncaughtExceptionHandler].
 *
 * Non-matching exceptions are left in place for [TestUncaughtExceptionHandler.assertAllExceptionAreCaught].
 *
 * Use this when a test intentionally causes exceptions on background threads
 * (e.g., via coroutine exception handlers that re-throw in test mode).
 *
 * @return the list of drained exceptions matching the predicate
 */
@TestOnly
fun drainUncaughtExceptions(predicate: (Throwable) -> Boolean): List<Throwable> {
  val handler = Thread.getDefaultUncaughtExceptionHandler()
  return if (handler is TestUncaughtExceptionHandler) handler.drainExceptions(predicate) else emptyList()
}
