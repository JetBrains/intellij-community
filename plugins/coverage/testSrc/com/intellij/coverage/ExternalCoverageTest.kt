// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.rt.coverage.instrumentation.InstrumentationOptions
import com.intellij.rt.coverage.util.CoverageReport
import com.intellij.rt.coverage.util.ProjectDataLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class ExternalCoverageTest : CoverageIntegrationBaseTest() {
  @Test
  fun `test watching externally added coverage`(): Unit = runBlocking {
    assertNoSuites()
    val ijSuite = loadIJSuiteCopy()
    val ijSuiteFile = File(ijSuite.suites[0].coverageDataFileProvider.coverageDataFilePath)

    openSuiteAndWait(ijSuite)
    ExternalCoverageWatchManager.getInstance(myProject).addRootsToWatch(ijSuite.suites.toList())

    for (status in listOf(LineCoverage.FULL, LineCoverage.NONE)) {
      waitSuiteProcessing {
        // modify coverage data and overwrite existing
        val projectData = ProjectDataLoader.load(ijSuiteFile)
        val classData = projectData.getClassData("foo.bar.BarClass")
        val lineData = classData.getLineData(9)
        lineData.hits = if (status == LineCoverage.FULL) 1 else 0
        lineData.setStatus(status)
        CoverageReport.save(projectData, InstrumentationOptions.Builder().setDataFile(ijSuiteFile).build())

        // does not work without force refreshing
        VirtualFileManager.getInstance().asyncRefresh()
      }

      val projectData = ijSuite.coverageData!!
      val classData = projectData.getClassData("foo.bar.BarClass")
      val lineData = classData.getLineData(9)
      Assert.assertEquals(status, lineData.status.toByte())
    }

    closeSuite(ijSuite)
    assertNoSuites()
  }
}
