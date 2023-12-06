// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.actions

import com.intellij.coverage.CoverageIntegrationBaseTest
import com.intellij.coverage.CoverageSuite
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.util.containers.TreeTraversal
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert

class SuiteChooserTest : CoverageIntegrationBaseTest() {

  fun `test chooser dialog includes registered suites`(): Unit = runBlocking {
    val ijSuite = loadIJSuite().suites[0]
    val jacocoSuite = loadJaCoCoSuite().suites[0]
    val xmlSuite = loadXMLSuite().suites[0]
    registerSuite(ijSuite)
    registerSuite(jacocoSuite)
    registerSuite(xmlSuite)

    val dialog = openChooserDialog()

    val suites = collectSuiteStates(dialog)
    Assert.assertEquals(3, suites.size)
    Assert.assertTrue(ijSuite in suites)
    Assert.assertFalse(suites[ijSuite]!!)
    Assert.assertTrue(jacocoSuite in suites)
    Assert.assertFalse(suites[jacocoSuite]!!)
    Assert.assertTrue(xmlSuite in suites)
    Assert.assertFalse(suites[xmlSuite]!!)

    closeDialog(dialog)
  }

  fun `test chooser dialog includes opened suites selected`(): Unit = runBlocking {
    val ijBundle = loadIJSuite()
    val ijSuite = ijBundle.suites[0]
    registerSuite(ijSuite)
    openSuiteAndWait(ijBundle)

    val xmlBundle = loadXMLSuite()
    val xmlSuite = xmlBundle.suites[0]
    registerSuite(xmlSuite)

    val dialog = openChooserDialog()

    val suites = collectSuiteStates(dialog)
    Assert.assertEquals(2, suites.size)
    Assert.assertTrue(ijSuite in suites)
    Assert.assertTrue(suites[ijSuite]!!)
    Assert.assertTrue(xmlSuite in suites)
    Assert.assertFalse(suites[xmlSuite]!!)

    closeSuite(ijBundle)
    closeDialog(dialog)
  }

  fun `test suite chooser opens checked suite`(): Unit = runBlocking {
    val ijSuite = loadIJSuite().suites[0]
    registerSuite(ijSuite)

    val dialog = openChooserDialog()
    collectSuiteNodes(dialog)[ijSuite]!!.isChecked = true

    withContext(Dispatchers.Main) {
      waitSuiteProcessing {
        dialog.doOKAction()
      }
    }

    val currentBundle = manager.currentSuitesBundle
    Assert.assertEquals(1, currentBundle.suites.size)
    Assert.assertTrue(ijSuite in currentBundle.suites)

    closeDialog(dialog)
  }

  fun `test suite chooser opens all checked suites`(): Unit = runBlocking {
    val ijSuite = loadIJSuite().suites[0]
    registerSuite(ijSuite)

    val xmlSuite = loadXMLSuite().suites[0]
    registerSuite(xmlSuite)

    val dialog = openChooserDialog()
    val suiteNodes = collectSuiteNodes(dialog)
    suiteNodes[ijSuite]!!.isChecked = true
    suiteNodes[xmlSuite]!!.isChecked = true

    withContext(Dispatchers.Main) {
      waitSuiteProcessing {
        dialog.doOKAction()
      }
    }

    val bundles = manager.activeSuites()
    Assert.assertEquals(2, bundles.size)
    val allSuites = bundles.flatMap { it.suites.toList() }
    Assert.assertEquals(2, allSuites.size)
    Assert.assertTrue(ijSuite in allSuites)
    Assert.assertTrue(xmlSuite in allSuites)

    for (bundle in bundles) {
      closeSuite(bundle)
    }

    closeDialog(dialog)
  }

  fun `test chooser dialog closes suites if not selected`(): Unit = runBlocking {
    val ijBundle = loadIJSuite()
    val ijSuite = ijBundle.suites[0]
    registerSuite(ijSuite)
    openSuiteAndWait(ijBundle)

    val dialog = openChooserDialog()
    val suiteNodes = collectSuiteNodes(dialog)
    suiteNodes[ijSuite]!!.isChecked = false

    withContext(Dispatchers.Main) {
      dialog.doOKAction()
    }

    val bundles = manager.activeSuites()
    Assert.assertTrue(bundles.isEmpty())

    closeDialog(dialog)
  }

  fun `test no coverage action closes all opened suites`(): Unit = runBlocking {
    val ijBundle = loadIJSuite()
    val ijSuite = ijBundle.suites[0]
    registerSuite(ijSuite)
    openSuiteAndWait(ijBundle)

    Assert.assertFalse(manager.activeSuites().isEmpty())

    withContext(Dispatchers.Main) {
      val dialog = CoverageSuiteChooserDialog(myProject)
      dialog.NoCoverageAction().doAction(null)
      closeDialog(dialog)
    }

    Assert.assertTrue(manager.activeSuites().isEmpty())
  }

  private suspend fun openChooserDialog(): CoverageSuiteChooserDialog =
    withContext(Dispatchers.Main) { return@withContext CoverageSuiteChooserDialog(myProject) }

  private suspend fun closeDialog(dialog: CoverageSuiteChooserDialog) {
    withContext(Dispatchers.Main) {
      Disposer.dispose(dialog.disposable)
    }
  }

  private fun collectSuiteNodes(dialog: CoverageSuiteChooserDialog): Map<CoverageSuite, CheckedTreeNode> {
    val tree = dialog.preferredFocusedComponent as CheckboxTree
    return TreeUtil.treeTraverser(tree).traverse(TreeTraversal.PRE_ORDER_DFS).toList()
      .filterIsInstance<CheckedTreeNode>()
      .filter { it.userObject is CoverageSuite }
      .associateBy { it.userObject as CoverageSuite }
  }

  private fun collectSuiteStates(dialog: CoverageSuiteChooserDialog): Map<CoverageSuite, Boolean> =
    collectSuiteNodes(dialog).mapValues { it.value.isChecked }

  private fun registerSuite(suite: CoverageSuite) {
    // Use 'presentableName' parameter to avoid report file deletion
    manager.addCoverageSuite(suite, suite.presentableName)
  }
}
