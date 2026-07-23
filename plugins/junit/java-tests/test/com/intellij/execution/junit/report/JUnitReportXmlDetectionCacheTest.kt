// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.report

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class JUnitReportXmlDetectionCacheTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  fun testChangedFileRevisionIsRetried() {
    val notifications = TestEditorNotifications()
    project.replaceService(EditorNotifications::class.java, notifications, testRootDisposable)
    val file = BlockingLightVirtualFile()
    val provider = JUnitReportFileEditorNotificationProvider()

    assertNull(provider.collectNotificationData(project, file))
    assertTrue(file.awaitDetectionStarted())

    file.changeRevisionAndContinueDetection()

    assertSame(file, notifications.awaitUpdate())
    assertNull(provider.collectNotificationData(project, file))
    assertSame(file, notifications.awaitUpdate())
    assertNotNull(provider.collectNotificationData(project, file))
  }

  private class BlockingLightVirtualFile : LightVirtualFile("report.xml", "<testsuite/>") {
    private val detectionStarted = CountDownLatch(1)
    private val continueDetection = CountDownLatch(1)

    override fun getInputStream(): InputStream {
      detectionStarted.countDown()
      check(continueDetection.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Timed out waiting to continue detection" }
      return super.getInputStream()
    }

    fun awaitDetectionStarted(): Boolean = detectionStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)

    fun changeRevisionAndContinueDetection() {
      modificationStamp += 1
      continueDetection.countDown()
    }
  }

  private class TestEditorNotifications : EditorNotifications() {
    private val updatedFiles = LinkedBlockingQueue<VirtualFile>()

    override fun updateNotifications(file: VirtualFile) {
      updatedFiles.add(file)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun updateNotifications(provider: EditorNotificationProvider) = Unit

    override fun updateAllNotifications() = Unit

    fun awaitUpdate(): VirtualFile? = updatedFiles.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)
  }

  private companion object {
    const val TIMEOUT_SECONDS = 10L
  }
}
