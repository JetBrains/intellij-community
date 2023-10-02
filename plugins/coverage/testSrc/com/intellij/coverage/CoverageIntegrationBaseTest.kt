// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.coverage.xml.XMLReportEngine
import com.intellij.coverage.xml.XMLReportRunner
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.JavaModuleTestCase
import com.intellij.testFramework.PlatformTestUtil
import java.io.File
import java.nio.file.Paths

abstract class CoverageIntegrationBaseTest : JavaModuleTestCase() {
  protected fun getTestDataPath(): String {
    return PluginPathManager.getPluginHomePath("coverage") + "/testData/simple"
  }

  override fun setUpProject() {
    myProject = PlatformTestUtil.loadAndOpenProject(Paths.get(getTestDataPath()), getTestRootDisposable())
  }


  @JvmOverloads
  protected fun loadIJSuite(includeFilters: Array<String>? = null, path: String = SIMPLE_IJ_REPORT_PATH) =
    loadCoverageSuite(JavaCoverageEngine::class.java, IDEACoverageRunner::class.java, path, includeFilters)

  @JvmOverloads
  protected fun loadJaCoCoSuite(includeFilters: Array<String>? = null, path: String = SIMPLE_JACOCO_REPORT_PATH) =
    loadCoverageSuite(JavaCoverageEngine::class.java, JaCoCoCoverageRunner::class.java, path, includeFilters)

  @JvmOverloads
  protected fun loadXMLSuite(includeFilters: Array<String>? = null, path: String = SIMPLE_XML_REPORT_PATH)
    = loadCoverageSuite(XMLReportEngine::class.java, XMLReportRunner::class.java, path, includeFilters)

  protected fun closeSuite() {
    CoverageDataManager.getInstance(myProject).chooseSuitesBundle(null)
  }

  protected fun openSuiteAndWait(bundle: CoverageSuitesBundle) {
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

  protected fun createCoverageFileProvider(coverageDataPath: String): CoverageFileProvider {
    val coverageFile = File(getTestDataPath(), coverageDataPath)
    val fileProvider: CoverageFileProvider = DefaultCoverageFileProvider(coverageFile)
    return fileProvider
  }

  private fun loadCoverageSuite(coverageEngineClass: Class<out CoverageEngine>, coverageRunnerClass: Class<out CoverageRunner>,
                                coverageDataPath: String,
                                includeFilters: Array<String>?): CoverageSuitesBundle {
    val runner = CoverageRunner.getInstance(coverageRunnerClass)
    val fileProvider: CoverageFileProvider = createCoverageFileProvider(coverageDataPath)
    val engine = CoverageEngine.EP_NAME.findExtensionOrFail(coverageEngineClass)
    val suite: CoverageSuite = engine.createCoverageSuite(
      runner, coverageDataPath, fileProvider, includeFilters,
      -1, null, false, false, false, myProject)!!
    return CoverageSuitesBundle(suite)
  }

  companion object {
    const val SIMPLE_IJ_REPORT_PATH = "simple\$foo_in_simple.coverage"
    const val SIMPLE_XML_REPORT_PATH = "simple\$foo_in_simple.coverage.xml"
    const val SIMPLE_JACOCO_REPORT_PATH = "simple\$foo_in_simple.jacoco.coverage"
  }
}
