// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageIntegrationBaseTest
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val TIMEOUT_MS = 20_000L

@RunWith(JUnit4::class)
class CoverageViewTest : CoverageIntegrationBaseTest() {

  @Test(timeout = TIMEOUT_MS)
  fun `test coverage toolwindow exists`() = runBlocking {
    val bundle = loadIJSuite()

    assertToolWindowDoesNotExist()
    openSuiteAndWait(bundle)
    assertToolWindowExists()
    Assert.assertNotNull(findCoverageView(bundle))

    closeSuite(bundle)
    assertToolWindowExists()
    Assert.assertNull(findCoverageView(bundle))
  }

  @Test(timeout = TIMEOUT_MS)
  fun `test several suites`() = runBlocking {
    val ijSuite = loadIJSuite()
    val xmlSuite = loadXMLSuite()

    assertToolWindowDoesNotExist()
    openSuiteAndWait(ijSuite)
    assertToolWindowExists()
    Assert.assertNotNull(findCoverageView(ijSuite))

    openSuiteAndWait(xmlSuite)
    assertToolWindowExists()
    Assert.assertNotNull(findCoverageView(ijSuite))
    Assert.assertNotNull(findCoverageView(xmlSuite))


    closeSuite(ijSuite)
    assertToolWindowExists()
    Assert.assertNull(findCoverageView(ijSuite))
    Assert.assertNotNull(findCoverageView(xmlSuite))

    closeSuite(xmlSuite)
    assertToolWindowExists()
    Assert.assertNull(findCoverageView(ijSuite))
    Assert.assertNull(findCoverageView(xmlSuite))
  }

  @Test(timeout = TIMEOUT_MS)
  fun `test call to service does not create tool window`() {
    assertToolWindowDoesNotExist()
    val manager = CoverageViewManager.getInstance(myProject)
    assertToolWindowDoesNotExist()
    manager.openedSuite
    assertToolWindowDoesNotExist()
  }

  private fun findCoverageView(bundle: CoverageSuitesBundle): CoverageView? = CoverageViewManager.getInstance(myProject).getView(bundle)
  private fun getCoverageToolWindow() = ToolWindowManager.getInstance(myProject).getToolWindow(CoverageViewManager.TOOLWINDOW_ID)
  private fun assertToolWindowExists() = Assert.assertNotNull(getCoverageToolWindow())
  private fun assertToolWindowDoesNotExist() = Assert.assertNull(getCoverageToolWindow())
}
