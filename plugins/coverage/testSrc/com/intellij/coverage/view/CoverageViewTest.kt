// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageIntegrationBaseTest
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
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

  private fun findCoverageView(bundle: CoverageSuitesBundle): CoverageView? =
    CoverageViewManager.getInstance(myProject).getToolwindow(bundle)

  private fun assertToolWindowExists() {
    Assert.assertNotNull(getInstance(myProject).getToolWindow(CoverageViewManager.TOOLWINDOW_ID))
  }
}
