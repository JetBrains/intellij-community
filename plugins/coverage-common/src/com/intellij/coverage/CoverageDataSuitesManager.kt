// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.util.ArrayUtilRt
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.io.File

private val LOG = logger<CoverageDataSuitesManager>()
private const val SUITE: @NonNls String = "SUITE"

/**
 * This manager tracks coverage suites added to the IDE.
 * @see com.intellij.coverage.actions.CoverageSuiteChooserDialog
 */
@State(name = "com.intellij.coverage.CoverageDataManagerImpl", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class CoverageDataSuitesManager(private val project: Project) : PersistentStateComponent<Element>, Disposable.Default {
  private val suites = ConcurrentCollectionFactory.createConcurrentSet<CoverageSuite>()

  init {
    setUpRunnerEPRemovedCallback()
    setUpEngineEPRemovedCallback()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): CoverageDataSuitesManager = project.service()
  }

  fun addSuite(coverageRunner: CoverageRunner,
               name: String,
               fileProvider: CoverageFileProvider,
               filters: Array<String?>?,
               lastCoverageTimeStamp: Long,
               suiteToMergeWith: String?,
               coverageByTestEnabled: Boolean,
               branchCoverage: Boolean): CoverageSuite? {
    return createCoverageSuite(coverageRunner, name, fileProvider, filters, lastCoverageTimeStamp, suiteToMergeWith,
                               coverageByTestEnabled, branchCoverage)
      ?.also { addSuite(it, suiteToMergeWith) }
  }

  fun addSuite(suite: CoverageSuite, suiteToMergeWith: String?) {
    if (suiteToMergeWith == null || suite.getPresentableName() != suiteToMergeWith) {
      deleteSuite(suite)
    }
    suites.remove(suite) // remove previous instance
    suites.add(suite) // add new instance
  }

  fun addExternalCoverageSuite(coverageRunner: CoverageRunner,
                               fileName: String,
                               fileProvider: CoverageFileProvider,
                               timeStamp: Long): CoverageSuite? {
    return createCoverageSuite(coverageRunner, fileName, fileProvider, ArrayUtilRt.EMPTY_STRING_ARRAY,
                               timeStamp, null, false, false)
      ?.also { suites.add(it) }
  }

  fun addSuite(config: CoverageEnabledConfiguration): CoverageSuite? {
    val name = CoverageBundle.message("coverage.results.suite.name", config.name)
    val path = config.getCoverageFilePath()
    LOG.assertTrue(path != null, "Configuration coverage report file is not configured ${config.name}")
    val runner = config.coverageRunner
    LOG.assertTrue(runner != null, "Cannot find coverage runner for ${path}")
    if (runner == null || path == null) return null

    val fileProvider = DefaultCoverageFileProvider(File(path))
    return createCoverageSuite(config, runner, name, fileProvider)
      ?.also {
        deleteSuite(it)
        suites.add(it)
      }
  }

  fun deleteSuite(suite: CoverageSuite) {
    suite.deleteCachedCoverageData()
    removeSuite(suite)
  }

  fun removeSuite(suite: CoverageSuite) {
    suites.remove(suite)
  }

  fun getSuites(): Array<CoverageSuite> = suites.toTypedArray()

  override fun loadState(element: Element) {
    for (suiteElement in element.getChildren(SUITE)) {
      val coverageRunner = BaseCoverageSuite.readRunnerAttribute(suiteElement)
      // skip unknown runners
      if (coverageRunner == null) continue

      var suite: CoverageSuite? = null
      for (engine in CoverageEngine.EP_NAME.extensions) {
        if (coverageRunner.acceptsCoverageEngine(engine)) {
          suite = engine.createEmptyCoverageSuite(coverageRunner)
          if (suite != null) {
            if (suite is BaseCoverageSuite) {
              suite.project = project
            }
            break
          }
        }
      }
      if (suite != null) {
        try {
          suite.readExternal(suiteElement)
          suites.add(suite)
        }
        catch (e: NumberFormatException) { // try next suite
        }
      }
    }
  }

  override fun getState(): Element {
    val element = Element("state")
    for (coverageSuite in suites) {
      val suiteElement = Element(SUITE)
      element.addContent(suiteElement)
      coverageSuite.writeExternal(suiteElement)
    }
    return element
  }

  private fun createCoverageSuite(config: CoverageEnabledConfiguration,
                                  runner: CoverageRunner,
                                  name: String,
                                  fileProvider: DefaultCoverageFileProvider): CoverageSuite? {
    for (engine in CoverageEngine.EP_NAME.extensions) {
      if (runner.acceptsCoverageEngine(engine) && engine.isApplicableTo(config.configuration)) {
        val suite = engine.createCoverageSuite(runner, name, fileProvider, config)
        if (suite != null) return suite
      }
    }
    LOG.error("Cannot create coverage suite for runner: " + runner.getPresentableName())
    return null
  }

  private fun createCoverageSuite(runner: CoverageRunner,
                                  name: String,
                                  fileProvider: CoverageFileProvider,
                                  filters: Array<String?>?,
                                  lastCoverageTimeStamp: Long,
                                  suiteToMergeWith: String?,
                                  coverageByTestEnabled: Boolean,
                                  branchCoverage: Boolean): CoverageSuite? {
    for (engine in CoverageEngine.EP_NAME.extensions) {
      if (!runner.acceptsCoverageEngine(engine)) continue
      val suite = engine.createCoverageSuite(runner, name, fileProvider, filters, lastCoverageTimeStamp, suiteToMergeWith,
                                             coverageByTestEnabled, branchCoverage, false, project)
      if (suite != null) return suite
    }

    LOG.error("Cannot create coverage suite for runner: " + runner.getPresentableName())
    return null
  }

  private fun setUpRunnerEPRemovedCallback() {
    CoverageRunner.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<CoverageRunner> {
      override fun extensionRemoved(extension: CoverageRunner, pluginDescriptor: PluginDescriptor) {
        for (suite in suites) {
          if (suite is BaseCoverageSuite) {
            if (suite.getRunner() === extension) {
              suite.runner = null
            }
          }
        }
      }
    }, this)
  }

  private fun setUpEngineEPRemovedCallback() {
    CoverageEngine.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<CoverageEngine> {
      override fun extensionRemoved(extension: CoverageEngine, pluginDescriptor: PluginDescriptor) {
        suites.removeIf { suite -> suite.getCoverageEngine() === extension }
      }
    }, this)
  }
}
