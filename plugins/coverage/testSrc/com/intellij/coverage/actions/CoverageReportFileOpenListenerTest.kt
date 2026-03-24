// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.actions

import com.intellij.coverage.CoverageIntegrationBaseTest
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val TIMEOUT_MS = 20_000L

@RunWith(JUnit4::class)
class JaCoCoCoverageFileTypeTest : CoverageIntegrationBaseTest() {

  @Test(timeout = TIMEOUT_MS)
  fun `test opening jacoco exec report imports suite and closes file editor`(): Unit = runBlocking {
    assertNoSuites()
    val reportFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(SIMPLE_JACOCO_REPORT_PATH))!!
    val fileEditorManager = FileEditorManager.getInstance(myProject)

    withContext(Dispatchers.EDT) {
      waitSuiteProcessing {
        OpenFileDescriptor(myProject, reportFile).navigate(true)
      }
    }

    val activeBundles = manager.activeSuites()
    Assert.assertEquals(1, activeBundles.size)
    val importedSuite = activeBundles.single().suites.single()
    Assert.assertTrue(FileUtil.pathsEqual(importedSuite.coverageDataFileName, reportFile.path))

    withTimeout(5.seconds) {
      while (withContext(Dispatchers.EDT) { fileEditorManager.isFileOpen(reportFile) }) {
        delay(100.milliseconds)
      }
    }

    closeSuite(activeBundles.single())
    manager.unregisterCoverageSuite(importedSuite)
    assertNoSuites()
  }
}
