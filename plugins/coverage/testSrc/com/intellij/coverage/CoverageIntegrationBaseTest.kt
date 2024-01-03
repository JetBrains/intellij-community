// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.coverage.xml.XMLReportEngine
import com.intellij.coverage.xml.XMLReportRunner
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.JavaModuleTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

abstract class CoverageIntegrationBaseTest : JavaModuleTestCase() {
  override fun runInDispatchThread() = false

  override fun tearDown(): Unit = runBlocking(Dispatchers.EDT) {
    super.tearDown()
  }

  override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path = Paths.get(getTestDataPath())

  val manager get() = CoverageDataManager.getInstance(myProject) as CoverageDataManagerImpl


  @JvmOverloads
  protected fun loadIJSuite(includeFilters: Array<String>? = DEFAULT_FILTER, path: String = SIMPLE_IJ_REPORT_PATH) =
    loadCoverageSuite(JavaCoverageEngine::class.java, IDEACoverageRunner::class.java, path, includeFilters)

  @JvmOverloads
  protected fun loadJaCoCoSuite(includeFilters: Array<String>? = DEFAULT_FILTER, path: String = SIMPLE_JACOCO_REPORT_PATH) =
    loadCoverageSuite(JavaCoverageEngine::class.java, JaCoCoCoverageRunner::class.java, path, includeFilters)

  @JvmOverloads
  protected fun loadXMLSuite(includeFilters: Array<String>? = null, path: String = SIMPLE_XML_REPORT_PATH) =
    loadCoverageSuite(XMLReportEngine::class.java, XMLReportRunner::class.java, path, includeFilters)

  protected fun closeSuite(bundle: CoverageSuitesBundle) {
    manager.closeSuitesBundle(bundle)
  }

  protected suspend fun openSuiteAndWait(bundle: CoverageSuitesBundle) = waitSuiteProcessing {
    manager.chooseSuitesBundle(bundle)
  }

  protected suspend fun waitSuiteProcessing(action: () -> Unit) {
    var dataCollected = false
    val disposable = Disposer.newDisposable()
    manager.addSuiteListener(object : CoverageSuiteListener {
      override fun coverageDataCalculated(bundle: CoverageSuitesBundle) {
        dataCollected = true
      }
    }, disposable)
    action()

    withTimeout(10_000) {
      // wait until data collected
      while (!dataCollected) delay(1)
    }
    Disposer.dispose(disposable)
  }

  private fun createCoverageFileProvider(coverageDataPath: String) =
    DefaultCoverageFileProvider(File(coverageDataPath))

  private fun loadCoverageSuite(coverageEngineClass: Class<out CoverageEngine>, coverageRunnerClass: Class<out CoverageRunner>,
                                coverageDataPath: String,
                                includeFilters: Array<String>?): CoverageSuitesBundle {
    val runner = CoverageRunner.getInstance(coverageRunnerClass)
    val fileProvider: CoverageFileProvider = createCoverageFileProvider(coverageDataPath)
    Assert.assertTrue(File(fileProvider.coverageDataFilePath).exists())
    val engine = CoverageEngine.EP_NAME.findExtensionOrFail(coverageEngineClass)
    val suite: CoverageSuite = engine.createCoverageSuite(
      runner, coverageDataPath, fileProvider, includeFilters,
      -1, null, true, true, false, myProject)!!
    return CoverageSuitesBundle(suite)
  }

  protected fun loadIJSuiteCopy(): CoverageSuitesBundle {
    val ijSuiteFile = FileUtil.createTempFile("coverage", ".ic").apply {
      deleteOnExit()
      val originalIJSuite = File(createCoverageFileProvider(SIMPLE_IJ_REPORT_PATH).coverageDataFilePath)
      originalIJSuite.copyTo(this, overwrite = true)
    }

    return loadIJSuite(path = ijSuiteFile.absolutePath)
  }

  protected fun assertNoSuites() {
    Assert.assertNull(manager.currentSuitesBundle)
    Assert.assertEquals(0, manager.suites.size)
  }

  companion object {
    protected fun getTestDataPath() = PluginPathManager.getPluginHomePath("coverage") + "/testData/simple"

    val SIMPLE_IJ_REPORT_PATH: String = File(getTestDataPath(), "simple\$foo_in_simple.ic").path
    val SIMPLE_XML_REPORT_PATH: String = File(getTestDataPath(), "simple\$foo_in_simple.xml").path
    val SIMPLE_JACOCO_REPORT_PATH: String = File(getTestDataPath(), "simple\$foo_in_simple.exec").path
    val DEFAULT_FILTER = arrayOf("foo.*")
  }
}
