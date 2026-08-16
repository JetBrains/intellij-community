// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.report

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class JUnitReportXmlDetectionCacheTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  fun testRevisionChangedDuringDetectionIsRetried() {
    val file = BlockingLightVirtualFile()

    try {
      // A cache miss schedules detection for the initial file revision.
      assertFalse(JUnitReportXmlDetector.looksLikeJUnitReportFile(project, file))
      file.awaitDetectionStarted()
      file.changeRevision()
    }
    finally {
      file.continueDetection()
    }

    waitForDetection()
    assertEquals(1, file.inputStreamAccessCount)
    // The stale result was discarded, so this lookup schedules detection for the current file revision.
    assertFalse(JUnitReportXmlDetector.looksLikeJUnitReportFile(project, file))

    waitForDetection()
    // The retried detection result is now cached.
    assertTrue(JUnitReportXmlDetector.looksLikeJUnitReportFile(project, file))
    assertEquals(2, file.inputStreamAccessCount)
  }

  fun testDetectionResultIsCachedUntilFileRevisionChanges() {
    val file = TrackingLightVirtualFile()

    // A cache miss schedules detection for the initial file revision.
    assertFalse(JUnitReportXmlDetector.looksLikeJUnitReportFile(project, file))
    waitForDetection()
    // Repeated lookups reuse the cached result without reopening the file.
    assertTrue(JUnitReportXmlDetector.looksLikeJUnitReportFile(project, file))
    assertTrue(JUnitReportXmlDetector.looksLikeJUnitReportFile(project, file))
    assertEquals(1, file.inputStreamAccessCount)

    file.changeRevision()

    // The revision mismatch discards the cached result and schedules detection again.
    assertFalse(JUnitReportXmlDetector.looksLikeJUnitReportFile(project, file))
    waitForDetection()
    // Repeated lookups reuse the new cached result without reopening the file.
    assertTrue(JUnitReportXmlDetector.looksLikeJUnitReportFile(project, file))
    assertTrue(JUnitReportXmlDetector.looksLikeJUnitReportFile(project, file))
    assertEquals(2, file.inputStreamAccessCount)
  }

  private open class TrackingLightVirtualFile : LightVirtualFile("report.xml", "<testsuite/>") {
    private val inputStreamAccesses = AtomicInteger()

    val inputStreamAccessCount: Int
      get() = inputStreamAccesses.get()

    override fun getInputStream(): InputStream {
      inputStreamAccesses.incrementAndGet()
      return super.getInputStream()
    }

    fun changeRevision() {
      modificationStamp += 1
    }
  }

  private class BlockingLightVirtualFile : TrackingLightVirtualFile() {
    private val detectionStarted = CountDownLatch(1)
    private val detectionMayContinue = CountDownLatch(1)

    override fun getInputStream(): InputStream {
      detectionStarted.countDown()
      detectionMayContinue.await()
      return super.getInputStream()
    }

    fun awaitDetectionStarted() {
      detectionStarted.await()
    }

    fun continueDetection() = detectionMayContinue.countDown()
  }

  private fun waitForDetection() {
    runInEdtAndWait {
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    }
  }
}
