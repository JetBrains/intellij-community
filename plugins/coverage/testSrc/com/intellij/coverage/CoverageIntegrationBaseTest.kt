// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.coverage.xml.XMLReportEngine
import com.intellij.coverage.xml.XMLReportRunner
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.JavaModuleTestCase
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.*
import org.junit.Assert
import java.io.File
import java.nio.file.Paths

abstract class CoverageIntegrationBaseTest : JavaModuleTestCase() {
  override fun runInDispatchThread() = false

  override fun tearDown(): Unit = runBlocking {
    withContext(Dispatchers.EDT) {
      super.tearDown()
    }
  }

  override fun setUpProject() {
    myProject = PlatformTestUtil.loadAndOpenProject(Paths.get(getTestDataPath()), getTestRootDisposable())
  }


  @JvmOverloads
  protected fun loadIJSuite(includeFilters: Array<String>? = DEFAULT_FILTER, path: String = SIMPLE_IJ_REPORT_PATH) =
    loadCoverageSuite(JavaCoverageEngine::class.java, IDEACoverageRunner::class.java, path, includeFilters)

  @JvmOverloads
  protected fun loadJaCoCoSuite(includeFilters: Array<String>? = DEFAULT_FILTER, path: String = SIMPLE_JACOCO_REPORT_PATH) =
    loadCoverageSuite(JavaCoverageEngine::class.java, JaCoCoCoverageRunner::class.java, path, includeFilters)

  @JvmOverloads
  protected fun loadXMLSuite(includeFilters: Array<String>? = null, path: String = SIMPLE_XML_REPORT_PATH)
    = loadCoverageSuite(XMLReportEngine::class.java, XMLReportRunner::class.java, path, includeFilters)

  protected fun closeSuite() {
    CoverageDataManager.getInstance(myProject).chooseSuitesBundle(null)
  }

  protected suspend fun openSuiteAndWait(bundle: CoverageSuitesBundle) = waitSuiteProcessing {
    CoverageDataManager.getInstance(myProject).chooseSuitesBundle(bundle)
  }

  protected suspend fun waitSuiteProcessing(action: () -> Unit) {
    var dataCollected = false
    val disposable = Disposer.newDisposable()
    CoverageDataManager.getInstance(myProject).addSuiteListener(object : CoverageSuiteListener {
      override fun coverageDataCalculated() {
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

  protected fun createCoverageFileProvider(coverageDataPath: String) =
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

  companion object {
    protected fun getTestDataPath() = PluginPathManager.getPluginHomePath("coverage") + "/testData/simple"

    val SIMPLE_IJ_REPORT_PATH = File(getTestDataPath(), "simple\$foo_in_simple.ic").path
    val SIMPLE_XML_REPORT_PATH = File(getTestDataPath(), "simple\$foo_in_simple.xml").path
    val SIMPLE_JACOCO_REPORT_PATH = File(getTestDataPath(), "simple\$foo_in_simple.exec").path
    val DEFAULT_FILTER = arrayOf("foo.*")
  }
}
