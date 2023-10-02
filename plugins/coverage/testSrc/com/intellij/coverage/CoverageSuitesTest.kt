// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import org.junit.Assert

class CoverageSuitesTest : CoverageIntegrationBaseTest() {
  fun `test external suite adding`() {
    val dataManager = CoverageDataManager.getInstance(myProject)
    val path = SIMPLE_IJ_REPORT_PATH
    val runner = CoverageRunner.getInstance(IDEACoverageRunner::class.java)
    val suite = dataManager.addExternalCoverageSuite(path, -1, runner, createCoverageFileProvider(path))

    dataManager.suites.run {
      Assert.assertEquals(1, size)
      Assert.assertSame(suite, this[0])
    }

    dataManager.unregisterCoverageSuite(suite)
    Assert.assertTrue(dataManager.suites.isEmpty())
  }

  fun `test coverage reopen if one of the suites is deleted`() {
    val dataManager = CoverageDataManager.getInstance(myProject) as CoverageDataManagerImpl
    val ijSuite = loadIJSuite().suites[0]
    val jacocoSuite = loadJaCoCoSuite().suites[0]

    // Use 'presentableName' parameter to avoid report file deletion
    dataManager.addCoverageSuite(ijSuite, ijSuite.presentableName)
    dataManager.addCoverageSuite(jacocoSuite, jacocoSuite.presentableName)

    dataManager.suites.run {
      Assert.assertEquals(2, size)
      Assert.assertTrue(ijSuite in this)
      Assert.assertTrue(jacocoSuite in this)
    }

    val bundle = CoverageSuitesBundle(arrayOf(ijSuite, jacocoSuite))

    openSuiteAndWait(bundle)
    Assert.assertSame(bundle, dataManager.currentSuitesBundle)

    dataManager.unregisterCoverageSuite(jacocoSuite)
    Assert.assertEquals(1, dataManager.suites.size)
    dataManager.currentSuitesBundle.also { currentBundle ->
      Assert.assertEquals(1, currentBundle.suites.size)
      Assert.assertSame(ijSuite, currentBundle.suites[0])
    }
    closeSuite()

    Assert.assertNull(dataManager.currentSuitesBundle)
    Assert.assertEquals(1, dataManager.suites.size)
    dataManager.unregisterCoverageSuite(ijSuite)
    Assert.assertEquals(0, dataManager.suites.size)
  }
}