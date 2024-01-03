// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageIntegrationBaseTest
import com.intellij.coverage.CoverageSuitesBundle
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val TIMEOUT_MS = 20_000L

@RunWith(JUnit4::class)
class CoverageTreeTest : CoverageIntegrationBaseTest() {

  @Test(timeout = TIMEOUT_MS)
  fun `test ij coverage tree contains elements`() = testCoverageSuiteTree(loadIJSuite())

  @Test(timeout = TIMEOUT_MS)
  fun `test xml coverage tree contains elements`() = testCoverageSuiteTree(loadXMLSuite())

  private fun testCoverageSuiteTree(suite: CoverageSuitesBundle): Unit = runBlocking {
    openSuiteAndWait(suite)

    val stateBean = CoverageViewManager.getInstance(myProject).stateBean
    val root = suite.coverageEngine.createCoverageViewExtension(myProject, suite, stateBean).createRootNode() as CoverageListNode
    Assert.assertNull(root.name)

    val fooNode = root.children.single() as CoverageListNode
    Assert.assertEquals("foo", fooNode.name)
    Assert.assertEquals(2, fooNode.children.size)

    val fooClassNode = fooNode.children[0] as CoverageListNode
    Assert.assertEquals("FooClass", fooClassNode.name)
    Assert.assertTrue(fooClassNode.children.isEmpty())

    val barNode = fooNode.children[1] as CoverageListNode
    Assert.assertEquals("bar", barNode.name)
    Assert.assertEquals(2, barNode.children.size)

    val uncoveredClassNode = barNode.children[0] as CoverageListNode
    Assert.assertEquals("UncoveredClass", uncoveredClassNode.name)
    Assert.assertTrue(uncoveredClassNode.children.isEmpty())

    val barClassNode = barNode.children[1] as CoverageListNode
    Assert.assertEquals("BarClass", barClassNode.name)
    Assert.assertTrue(barClassNode.children.isEmpty())

    closeSuite(suite)
  }
}
