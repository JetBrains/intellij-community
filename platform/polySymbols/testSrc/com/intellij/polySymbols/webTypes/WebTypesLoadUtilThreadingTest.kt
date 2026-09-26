// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes

import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.isInCancellableContext
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class WebTypesLoadUtilThreadingTest {

  private val minimalWebTypesJson =
    """{"name":"test","version":"2.0.0","contributions":{}}""".toByteArray()

  /**
   * Regression test for RIDER-138065.
   *
   * Before fix: guard `!isInCancellableContext()` misses EDT-with-Job case,
   * execution enters runWithCheckCanceled, assertRunBlockingBackgroundThreadAndNoWriteAction
   * fires LOG.error(IllegalStateException) — TestLoggerFactory rethrows it — test fails.
   *
   * After fix: `|| app.isDispatchThread` guard triggers, direct parse, no error.
   */
  @Test
  @RegistryKey("ide.run.blocking.cancellable.assert.in.tests", "true")
  fun `readWebTypes does not invoke runWithCheckCanceled on EDT with active coroutine Job`() {
    val app = ApplicationManager.getApplication()
    app.invokeAndWait {
      // replace=true: ApplicationImpl.invokeAndWait already installs a modality context element;
      // without replace=true, installThreadContext would fire "Thread context was already set"
      installThreadContext(Job(), replace = true) {
        assertTrue(isInCancellableContext(), "Job should make context cancellable")
        assertTrue(app.isDispatchThread, "Should be running on EDT")
        // Before fix: LOG.error fires -> TestLoggerFactory rethrows -> test fails
        // After fix:  EDT guard triggers -> direct parse -> test passes
        minimalWebTypesJson.inputStream().readWebTypes()
      }
    }
  }

  /**
   * Non-regression test for WEB-77220.
   * BGT callers with an active Job must still go through runWithCheckCanceled.
   * Verifies the fix does not accidentally disable cancellation for BGT callers.
   * Proves runWithCheckCanceled was entered by asserting the parse ran on a
   * different thread (blockingDispatcher dispatches to pool thread).
   *
   * @RegistryKey is set for symmetry with Test 1; it is a no-op here because
   * assertRunBlockingBackgroundThreadAndNoWriteAction short-circuits on non-EDT
   * at coroutines.kt:607 regardless of the registry flag.
   */
  @Test
  @RegistryKey("ide.run.blocking.cancellable.assert.in.tests", "true")
  fun `readWebTypes uses cancellable path on BGT with active coroutine Job`() = runBlocking {
    val app = ApplicationManager.getApplication()
    val callingThread = Thread.currentThread()
    assertFalse(app.isDispatchThread, "Should be on BGT (runBlocking thread)")
    assertTrue(isInCancellableContext(), "runBlocking installs a Job, making context cancellable")
    var parseThread: Thread? = null
    minimalWebTypesJson.inputStream().use { stream ->
      object : java.io.InputStream() {
        var captured = false
        override fun read(): Int = stream.read().also { if (!captured) { parseThread = Thread.currentThread(); captured = true } }
        override fun read(b: ByteArray): Int = stream.read(b).also { if (!captured) { parseThread = Thread.currentThread(); captured = true } }
        override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len).also { if (!captured) { parseThread = Thread.currentThread(); captured = true } }
        override fun available(): Int = stream.available()
        override fun close(): Unit = stream.close()
      }.readWebTypes()
    }
    assertNotNull(parseThread, "Parse must have been invoked")
    assertNotEquals(callingThread, parseThread,
      "runWithCheckCanceled should dispatch parse to blockingDispatcher, not the calling runBlocking thread")
  }

  /**
   * Verifies the pre-existing EDT path without a Job (third branch of the threading-rules table).
   * EDT callers without a coroutine Job must still take the direct branch and not trigger the assertion.
   * Guards against inverted-condition regressions (e.g. && instead of ||, or missing !).
   *
   * @RegistryKey is set for symmetry with Test 1; it is a no-op here because
   * !isInCancellableContext() is true (no Job) so the guard triggers before runWithCheckCanceled.
   */
  @Test
  @RegistryKey("ide.run.blocking.cancellable.assert.in.tests", "true")
  fun `readWebTypes takes direct path on EDT without coroutine Job`() {
    val app = ApplicationManager.getApplication()
    app.invokeAndWait {
      // No installThreadContext here — EDT thread has no Job in context
      assertFalse(isInCancellableContext(), "No Job installed, context should not be cancellable")
      assertTrue(app.isDispatchThread, "Should be on EDT")
      // Should succeed: !isInCancellableContext() guard triggers direct branch
      minimalWebTypesJson.inputStream().readWebTypes()
    }
  }
}
