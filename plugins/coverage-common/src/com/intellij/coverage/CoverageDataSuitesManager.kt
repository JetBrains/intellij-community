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
import org.jdom.Element
import org.jetbrains.annotations.NonNls

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

  fun addSuite(suite: CoverageSuite, suiteToMergeWith: String?) {
    if (suiteToMergeWith == null || suite.getPresentableName() != suiteToMergeWith) {
      deleteSuite(suite)
    }
    suites.remove(suite) // remove previous instance
    suites.add(suite) // add new instance
  }

  fun addExternalCoverageSuite(fileName: String,
                               runner: CoverageRunner,
                               fileProvider: CoverageFileProvider,
                               timestamp: Long): CoverageSuite? {
    return createCoverageSuite(fileName, runner, fileProvider, timestamp)
      ?.also { suites.add(it) }
  }

  fun addSuite(config: CoverageEnabledConfiguration): CoverageSuite? {
    val suite = createCoverageSuite(config)
    if (suite != null) {
      deleteSuite(suite)
      suites.add(suite)
    }
    return suite
  }

  fun deleteSuite(suite: CoverageSuite) {
    suite.deleteCachedCoverageData()
    removeSuite(suite)
  }

  fun removeSuite(suite: CoverageSuite) {
    suites.remove(suite)
  }

  fun getSuites(): Array<CoverageSuite> = suites.toTypedArray()

  fun createCoverageSuite(name: String,
                          runner: CoverageRunner,
                          fileProvider: CoverageFileProvider,
                          timestamp: Long): CoverageSuite? {
    return CoverageEngine.EP_NAME.extensionList
      .filter(runner::acceptsCoverageEngine)
      .firstNotNullOfOrNull { it.createCoverageSuite(name, project, runner, fileProvider, timestamp) }
      .also {
        if (it == null) {
          LOG.error("Cannot create coverage suite for runner: " + runner.getPresentableName())
        }
      }
  }

  override fun loadState(element: Element) {
    for (suiteElement in element.getChildren(SUITE)) {
      val coverageRunner = BaseCoverageSuite.readRunnerAttribute(suiteElement) ?: continue // skip unknown runners

      val suite = CoverageEngine.EP_NAME.extensionList.asSequence()
                    .filter { coverageRunner.acceptsCoverageEngine(it) }
                    .firstNotNullOfOrNull { it.createEmptyCoverageSuite(coverageRunner) } ?: continue
      if (suite is BaseCoverageSuite) {
        suite.project = project
      }

      try {
        suite.readExternal(suiteElement)
        suites.add(suite)
      }
      catch (e: NumberFormatException) { // try next suite
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

  private fun createCoverageSuite(config: CoverageEnabledConfiguration): CoverageSuite? {
    return CoverageEngine.EP_NAME.extensionList
      .filter { it.isApplicableTo(config.configuration) }
      .firstNotNullOfOrNull { it.createCoverageSuite(config) }.also {
        LOG.assertTrue(it != null, "Cannot create coverage suite for run config: ${config.javaClass.name}")
      }
  }

  private fun setUpRunnerEPRemovedCallback() {
    CoverageRunner.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<CoverageRunner> {
      override fun extensionRemoved(extension: CoverageRunner, pluginDescriptor: PluginDescriptor) {
        suites.removeIf { suite -> suite.runner === extension }
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
