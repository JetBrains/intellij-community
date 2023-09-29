// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageIntegrationBaseTest
import com.intellij.coverage.CoverageSuiteListener
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import org.junit.Assert

class CoverageViewTest : CoverageIntegrationBaseTest() {

  fun testViewActivation() {
    val bundle = loadIJSuite()

    openSuiteAndWait(bundle)
    assertToolWindowExists()
    Assert.assertNotNull(findCoverageView(bundle))

    closeSuite()
    assertToolWindowExists()
    Assert.assertNull(findCoverageView(bundle))
  }

  private fun findCoverageView(bundle: CoverageSuitesBundle): CoverageView? =
    CoverageViewManager.getInstance(myProject).getToolwindow(bundle)

  private fun assertToolWindowExists() {
    Assert.assertNotNull(getInstance(myProject).getToolWindow(CoverageViewManager.TOOLWINDOW_ID))
  }


  private fun closeSuite() {
    CoverageDataManager.getInstance(myProject).chooseSuitesBundle(null)
  }

  private fun openSuiteAndWait(bundle: CoverageSuitesBundle) {
    var dataCollected = false
    val disposable = object : Disposable.Default {}
    val listener = object : CoverageSuiteListener {
      override fun coverageDataCalculated() {
        dataCollected = true
      }
    }
    CoverageDataManager.getInstance(myProject).run {
      addSuiteListener(listener, disposable)
      chooseSuitesBundle(bundle)
    }

    // wait until data collected
    while (!dataCollected) Thread.yield()
    Disposer.dispose(disposable)
  }
}
