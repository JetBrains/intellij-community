// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import java.io.File

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

  fun `test coverage is closed when a suite is deleted`(): Unit = runBlocking {
    val dataManager = CoverageDataManager.getInstance(myProject)
    val ijSuite = loadIJSuite().suites[0]

    registerSuite(ijSuite)

    dataManager.suites.run {
      Assert.assertEquals(1, size)
      Assert.assertTrue(ijSuite in this)
    }

    val bundle = CoverageSuitesBundle(ijSuite)

    openSuiteAndWait(bundle)
    Assert.assertSame(bundle, dataManager.currentSuitesBundle)

    dataManager.unregisterCoverageSuite(ijSuite)

    Assert.assertEquals(0, dataManager.suites.size)
    Assert.assertNull(dataManager.currentSuitesBundle)
  }


  fun `test coverage reopen if one of the suites is deleted`(): Unit = runBlocking {
    val dataManager = CoverageDataManager.getInstance(myProject)
    val ijSuite = loadIJSuite().suites[0]
    val jacocoSuite = loadJaCoCoSuite().suites[0]

    registerSuite(ijSuite)
    registerSuite(jacocoSuite)

    dataManager.suites.run {
      Assert.assertEquals(2, size)
      Assert.assertTrue(ijSuite in this)
      Assert.assertTrue(jacocoSuite in this)
    }

    val bundle = CoverageSuitesBundle(arrayOf(ijSuite, jacocoSuite))

    openSuiteAndWait(bundle)
    Assert.assertSame(bundle, dataManager.currentSuitesBundle)

    waitSuiteProcessing {
      dataManager.unregisterCoverageSuite(jacocoSuite)
    }

    Assert.assertEquals(1, dataManager.suites.size)
    dataManager.currentSuitesBundle.also { currentBundle ->
      Assert.assertNotSame(bundle, currentBundle)
      Assert.assertEquals(1, currentBundle.suites.size)
      Assert.assertSame(ijSuite, currentBundle.suites[0])
      closeSuite(currentBundle)
    }

    Assert.assertNull(dataManager.currentSuitesBundle)
    Assert.assertEquals(1, dataManager.suites.size)
    dataManager.unregisterCoverageSuite(ijSuite)
    Assert.assertEquals(0, dataManager.suites.size)
  }

  fun `test suite removal with deletion asks for approval from user`() {
    val suite = loadIJSuiteCopy().suites[0]
    val dataManager = CoverageDataManager.getInstance(myProject)
    val file = File(suite.coverageDataFileName)

    registerSuite(suite)
    Assert.assertTrue(file.exists())

    try {
      dataManager.removeCoverageSuite(suite)
      Assert.fail("Should ask for approval, which was supposed to lead to RuntimeException")
    }
    catch (e: RuntimeException) {
      Assert.assertTrue(e.message!!.contains(file.absolutePath))
    }
  }

  fun `test suite is not opened if the report file does not exist`() {
    val suite = loadIJSuiteCopy()
    val dataManager = CoverageDataManager.getInstance(myProject)
    val file = File(suite.suites[0].coverageDataFileName)
    Assert.assertTrue(file.exists())
    file.delete()

    dataManager.chooseSuitesBundle(suite)
    Assert.assertNull(dataManager.currentSuitesBundle)
    Assert.assertEquals(0, dataManager.suites.size)
  }

  fun `test opening several suites`(): Unit = runBlocking {
    val dataManager = CoverageDataManager.getInstance(myProject)
    val ijSuite = loadIJSuite()
    val xmlSuite = loadXMLSuite()

    openSuiteAndWait(ijSuite)
    openSuiteAndWait(xmlSuite)

    Assert.assertSame(xmlSuite, dataManager.currentSuitesBundle)
    dataManager.activeSuites().also { activeSuites ->
      Assert.assertEquals(2, activeSuites.count())
      Assert.assertTrue(ijSuite in activeSuites)
      Assert.assertTrue(xmlSuite in activeSuites)
    }

    closeSuite(xmlSuite)

    Assert.assertSame(ijSuite, dataManager.currentSuitesBundle)
    dataManager.activeSuites().also { activeSuites ->
      Assert.assertEquals(1, activeSuites.count())
      Assert.assertTrue(ijSuite in activeSuites)
    }

    closeSuite(ijSuite)
    Assert.assertNull(dataManager.currentSuitesBundle)
    Assert.assertEquals(0, dataManager.activeSuites().size)
  }

  fun `test hide coverage action closes all suites`() : Unit = runBlocking {
    val dataManager = CoverageDataManager.getInstance(myProject)
    val ijSuite = loadIJSuite()
    val xmlSuite = loadXMLSuite()

    val hideAction = ActionUtil.getAction("HideCoverage")!!
    val dataContext = SimpleDataContext.getProjectContext(myProject)

    run {
      val actionEvent = AnActionEvent.createFromDataContext(ActionPlaces.MAIN_TOOLBAR, null, dataContext)
      hideAction.update(actionEvent)
      Assert.assertFalse(actionEvent.presentation.isEnabled)
    }

    openSuiteAndWait(ijSuite)
    openSuiteAndWait(xmlSuite)

    run {
      val actionEvent = AnActionEvent.createFromDataContext(ActionPlaces.MAIN_TOOLBAR, null, dataContext)
      hideAction.update(actionEvent)
      Assert.assertTrue(actionEvent.presentation.isEnabled)
      hideAction.actionPerformed(actionEvent)
    }

    Assert.assertNull(dataManager.currentSuitesBundle)
    Assert.assertEquals(0, dataManager.activeSuites().size)
  }

  private fun registerSuite(suite: CoverageSuite) {
    val dataManager = CoverageDataManager.getInstance(myProject) as CoverageDataManagerImpl
    // Use 'presentableName' parameter to avoid report file deletion
    dataManager.addCoverageSuite(suite, suite.presentableName)
  }
}