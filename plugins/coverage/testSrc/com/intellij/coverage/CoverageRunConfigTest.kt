// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.coverage.analysis.JavaCoverageAnnotator
import com.intellij.coverage.view.CoverageViewManager
import com.intellij.coverage.view.CoverageViewTreeStructure
import com.intellij.coverage.view.JavaCoverageNode
import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.classFilter.ClassFilter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jdom.Element
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds


@RunWith(JUnit4::class)
class CoverageRunConfigTest : CoverageIntegrationBaseTest() {
  override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path =
    Path.of(PluginPathManager.getPluginHomePath("coverage"), "testData/integration")

  @Test
  fun `test coverage run config creation`() {
    val runManager = RunManager.getInstance(project)
    val runConfig = runManager.findConfigurationByName("foo in integration")?.configuration as RunConfigurationBase<*>
    val coverageConfig = CoverageEnabledConfiguration.getOrCreate(runConfig)
    coverageConfig as JavaCoverageEnabledConfiguration

    Assert.assertFalse(coverageConfig.isTrackTestFolders)
    Assert.assertTrue(coverageConfig.coverageRunner is JaCoCoCoverageRunner)
    val includeConfigPattens = coverageConfig.patterns!!
    Assert.assertEquals(1, includeConfigPattens.size)
    Assert.assertEquals("foo.*", includeConfigPattens[0])
    Assert.assertTrue(coverageConfig.excludePatterns.isNullOrEmpty())
    Assert.assertNull(coverageConfig.currentCoverageSuite)

    val suite = JavaCoverageEngine.getInstance().createCoverageSuite(coverageConfig)
    suite as JavaCoverageSuite

    Assert.assertTrue(suite.isBranchCoverage)
    Assert.assertFalse(suite.isCoverageByTestEnabled)
    Assert.assertFalse(suite.isCoverageByTestApplicable)
    Assert.assertFalse(suite.isTrackTestFolders)
    Assert.assertTrue(suite.runner is JaCoCoCoverageRunner)
    Assert.assertTrue(suite.coverageEngine is JavaCoverageEngine)
    val includePattens = suite.includeFilters!!
    Assert.assertNotSame(includeConfigPattens, includePattens)
    Assert.assertEquals(1, includeConfigPattens.size)
    Assert.assertEquals("foo.*", includeConfigPattens[0])
    Assert.assertTrue(suite.excludePatterns.isNullOrEmpty())
  }

  @Test
  fun `test run with jacoco creates report and aggregated coverage tree stats`() =
    doTestRunWithCoverage(requireNotNull(CoverageRunner.getInstance(JaCoCoCoverageRunner::class.java)))

  @Test
  fun `test run with intellij coverage creates report and aggregated coverage tree stats`() =
    doTestRunWithCoverage(requireNotNull(CoverageRunner.getInstance(IDEACoverageRunner::class.java)))

  @Test
  fun `test explicit intellij coverage runner is persisted`() {
    val runManager = RunManager.getInstance(project)
    val runConfig = runManager.findConfigurationByName("foo in integration")?.configuration as RunConfigurationBase<*>
    val coverageConfig = CoverageEnabledConfiguration.getOrCreate(runConfig) as JavaCoverageEnabledConfiguration

    val defaultElement = Element("extension")
    coverageConfig.writeExternal(defaultElement)
    Assert.assertNull(defaultElement.getAttributeValue("runner"))

    val ideaRunner = requireNotNull(CoverageRunner.getInstance(IDEACoverageRunner::class.java))
    coverageConfig.coverageRunner = ideaRunner
    val ideaElement = Element("extension")
    coverageConfig.writeExternal(ideaElement)
    Assert.assertEquals(ideaRunner.id, ideaElement.getAttributeValue("runner"))

    val restored = JavaCoverageEnabledConfiguration(runConfig)
    restored.readExternal(ideaElement)
    Assert.assertSame(ideaRunner, restored.coverageRunner)
  }

  @Test
  fun `test suite without runner uses jacoco`() {
    val element = Element("suite")
      .setAttribute("FILE_PATH", SIMPLE_JACOCO_REPORT_PATH)
      .setAttribute("MODIFIED", "0")
    val suite = JavaCoverageSuite(JavaCoverageEngine.getInstance())

    suite.readExternal(element)

    Assert.assertTrue(suite.runner is JaCoCoCoverageRunner)
  }

  @Test
  fun `test one-shot coverage overrides do not change persisted options`() {
    val runConfig = RunManager.getInstance(project).findConfigurationByName("foo in integration")?.configuration as RunConfigurationBase<*>
    val optionsProvider = JavaCoverageOptionsProvider.getInstance(project)
    val jacocoRunner = CoverageRunner.getInstance(JaCoCoCoverageRunner::class.java)
    val ideaRunner = CoverageRunner.getInstance(IDEACoverageRunner::class.java)
    val persistedRunner = optionsProvider.coverageRunner
    val persistedBranchCoverage = optionsProvider.branchCoverage
    val persistedTestTracking = optionsProvider.testTracking
    val persistedTestModulesCoverage = optionsProvider.testModulesCoverage

    try {
      optionsProvider.coverageRunner = jacocoRunner
      optionsProvider.branchCoverage = true
      optionsProvider.testTracking = true
      optionsProvider.testModulesCoverage = false
      val effectiveRunConfig = runConfig.clone() as RunConfigurationBase<*>
      val effectiveCoverageConfig = CoverageEnabledConfiguration.getOrCreate(effectiveRunConfig)
      JavaCoverageEngine.setTemporaryOverrides(effectiveRunConfig, ideaRunner, false, false, true)
      val overriddenSuite = JavaCoverageEngine.getInstance().createCoverageSuite(effectiveCoverageConfig)
      val persistedSuite = JavaCoverageEngine.getInstance().createCoverageSuite(CoverageEnabledConfiguration.getOrCreate(runConfig))

      Assert.assertTrue(overriddenSuite?.runner is IDEACoverageRunner)
      Assert.assertFalse(overriddenSuite!!.isBranchCoverage)
      Assert.assertFalse(overriddenSuite.isCoverageByTestEnabled)
      Assert.assertTrue(overriddenSuite.isTrackTestFolders)
      Assert.assertTrue(persistedSuite?.runner is JaCoCoCoverageRunner)
      Assert.assertTrue(persistedSuite!!.isBranchCoverage)
      Assert.assertFalse(persistedSuite.isTrackTestFolders)
      Assert.assertTrue(optionsProvider.coverageRunner is JaCoCoCoverageRunner)
      Assert.assertTrue(optionsProvider.branchCoverage)
      Assert.assertTrue(optionsProvider.testTracking)
      Assert.assertFalse(optionsProvider.testModulesCoverage)
    }
    finally {
      optionsProvider.coverageRunner = persistedRunner
      optionsProvider.branchCoverage = persistedBranchCoverage
      optionsProvider.testTracking = persistedTestTracking
      optionsProvider.testModulesCoverage = persistedTestModulesCoverage
    }
  }

  @Test
  fun `test explicit coverage report path is used only for the configured run`() {
    val runConfig = RunManager.getInstance(project).findConfigurationByName("foo in integration")?.configuration as RunConfigurationBase<*>
    val persistedCoverageConfig = CoverageEnabledConfiguration.getOrCreate(runConfig)
    val originalPath = persistedCoverageConfig.coverageFilePath
    val mcpReportPath = Path.of(requireNotNull(project.basePath), "mcp-coverage.ic")
    val effectiveRunConfig = runConfig.clone() as RunConfigurationBase<*>
    val effectiveCoverageConfig = JavaCoverageEnabledConfiguration(effectiveRunConfig)
    effectiveRunConfig.putCopyableUserData(CoverageEnabledConfiguration.COVERAGE_KEY, effectiveCoverageConfig)
    effectiveCoverageConfig.coverageRunner = persistedCoverageConfig.coverageRunner
    effectiveCoverageConfig.setCoverageFilePathOverride(mcpReportPath.toString())

    val suite = JavaCoverageEngine.getInstance().createCoverageSuite(effectiveCoverageConfig)

    Assert.assertEquals(mcpReportPath.toString(), suite?.coverageDataFileName)
    Assert.assertEquals(originalPath, persistedCoverageConfig.coverageFilePath)

    effectiveCoverageConfig.setCoverageFilePathOverride(null)
    val pathBeforeRename = effectiveCoverageConfig.coverageFilePath
    effectiveRunConfig.name = "renamed coverage configuration"
    effectiveCoverageConfig.coverageRunner = effectiveCoverageConfig.coverageRunner

    Assert.assertNotEquals(pathBeforeRename, effectiveCoverageConfig.coverageFilePath)
  }

  @Test
  fun `test MCP coverage processing leaves active suite unchanged when option is ask`() = runBlocking {
    val runModule = requireNotNull(ModuleManager.getInstance(project).findModuleByName("integration"))
    val runConfig = ApplicationConfiguration("MCP coverage integration test", project).apply {
      setModule(runModule)
      mainClassName = "foo.CoverageApp"
      alternativeJrePath = requireNotNull(JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk.homePath)
      isAlternativeJrePathEnabled = true
    }
    val coverageConfig = CoverageEnabledConfiguration.getOrCreate(runConfig) as JavaCoverageEnabledConfiguration
    val runner = requireNotNull(CoverageRunner.getInstance(IDEACoverageRunner::class.java))
    coverageConfig.coverageRunner = runner
    JavaCoverageEngine.setTemporaryOverrides(runConfig, runner, true, false, false)
    val isolatedAnnotator = JavaCoverageAnnotator(project)
    CoverageDataManager.setSuppressedPresentation(runConfig, isolatedAnnotator)
    val options = CoverageOptionsProvider.getInstance(project)
    val previousOption = options.optionToReplace
    val activeBundle = loadIJSuite()
    openSuiteAndWait(activeBundle)
    val activeAnnotator = JavaCoverageAnnotator.getInstance(project)
    val activeClassCoverage = activeAnnotator.classesCoverage.toMap()
    val completed = CompletableDeferred<CoverageSuitesBundle>()
    manager.addSuiteListener(object : CoverageSuiteListener {
      override fun coverageDataCalculated(bundle: CoverageSuitesBundle) {
        completed.complete(bundle)
      }
    }, testRootDisposable)
    try {
      options.setOptionsToReplace(CoverageOptionsProvider.ASK_ON_NEW_SUITE)
      withContext(Dispatchers.EDT) {
        PlatformTestUtil.executeConfigurationAndWait(runConfig, CoverageExecutor.EXECUTOR_ID)
      }
      val completedBundle = withTimeout(10_000.milliseconds) { completed.await() }

      Assert.assertSame(activeBundle, manager.currentSuitesBundle)
      Assert.assertSame(isolatedAnnotator, completedBundle.getAnnotator(project))
      Assert.assertEquals(activeClassCoverage, activeAnnotator.classesCoverage)
      Assert.assertTrue(isolatedAnnotator.classesCoverage.isNotEmpty())
      Assert.assertFalse(completedBundle.shouldActivateToolWindow())
      Assert.assertEquals(CoverageOptionsProvider.ASK_ON_NEW_SUITE, options.optionToReplace)
    }
    finally {
      Disposer.dispose(isolatedAnnotator)
      coverageConfig.currentCoverageSuite?.let(manager::removeCoverageSuite)
      closeSuite(activeBundle)
      options.setOptionsToReplace(previousOption)
    }
  }

  private fun doTestRunWithCoverage(coverageRunner: CoverageRunner): Unit = runBlocking {
    val projectDir = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(getProjectDirOrFile(true)))
    VfsUtil.markDirtyAndRefresh(false, true, true, projectDir)
    IndexingTestUtil.waitUntilIndexesAreReady(project)

    val runModule = requireNotNull(ModuleManager.getInstance(project).findModuleByName("integration"))
    val coverageOptions = JavaCoverageOptionsProvider.getInstance(project)
    val previousRunner = coverageOptions.coverageRunner
    val coverageViewState = CoverageViewManager.getInstance(project).stateBean
    val previousFlattenPackages = coverageViewState.isFlattenPackages
    val previousHideFullyCovered = coverageViewState.isHideFullyCovered
    val previousShowOnlyModified = coverageViewState.isShowOnlyModified
    var reportPath: Path? = null
    var suite: JavaCoverageSuite? = null
    try {
      coverageViewState.isFlattenPackages = false
      coverageViewState.isHideFullyCovered = false
      coverageViewState.isShowOnlyModified = false
      val runConfig = ApplicationConfiguration("${coverageRunner.presentableName} integration test", project).apply {
        setModule(runModule)
        mainClassName = "foo.CoverageApp"
        alternativeJrePath = requireNotNull(JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk.homePath)
        isAlternativeJrePathEnabled = true
      }
      val coverageConfig = CoverageEnabledConfiguration.getOrCreate(runConfig) as JavaCoverageEnabledConfiguration
      coverageOptions.coverageRunner = coverageRunner
      coverageConfig.coverageRunner = coverageRunner
      Assert.assertSame(coverageRunner, coverageOptions.coverageRunner)
      Assert.assertSame(coverageRunner, coverageConfig.coverageRunner)
      coverageConfig.setCoveragePatterns(arrayOf(ClassFilter("foo.FooClass"), ClassFilter("foo.bar.UncoveredClass")))
      val coverageReport = Path.of(requireNotNull(coverageConfig.coverageFilePath))
      reportPath = coverageReport
      Files.deleteIfExists(coverageReport)

      waitSuiteProcessing {
        runBlocking(Dispatchers.EDT) {
          PlatformTestUtil.executeConfigurationAndWait(runConfig, CoverageExecutor.EXECUTOR_ID)
        }
      }

      suite = coverageConfig.currentCoverageSuite as JavaCoverageSuite
      Assert.assertSame(coverageRunner, suite.runner)
      Assert.assertTrue(Files.isRegularFile(coverageReport))
      Assert.assertTrue("The ${coverageRunner.presentableName} report must not be empty", Files.size(coverageReport) > 0)
      Assert.assertTrue(manager.currentSuitesBundle.contains(suite))

      val classData = requireNotNull(suite.coverageData?.getClassData("foo.FooClass"))
      Assert.assertEquals(LineCoverage.FULL.toInt(), classData.getLineData(5).status)

      val bundle = requireNotNull(manager.currentSuitesBundle)
      val treeStructure = CoverageViewTreeStructure(project, bundle)
      val rootNode = treeStructure.rootElement as AbstractTreeNode<*>
      val packageNode = treeStructure.getChildElements(rootNode).single() as JavaCoverageNode
      val packageChildren = treeStructure.getChildElements(packageNode).filterIsInstance<JavaCoverageNode>()
        .associateBy { it.qualifiedName }
      val classNode = requireNotNull(packageChildren["foo.FooClass"]) {
        "foo.FooClass is missing from coverage tree; available nodes: ${packageChildren.keys}"
      }
      val subPackageNode = requireNotNull(packageChildren["foo.bar"]) {
        "foo.bar is missing from coverage tree; available nodes: ${packageChildren.keys}; " +
        "report classes: ${suite.coverageData?.classesCollection?.map { it.name }}"
      }
      val uncoveredClassNode = treeStructure.getChildElements(subPackageNode).single() as JavaCoverageNode
      Assert.assertEquals("foo", packageNode.qualifiedName)
      Assert.assertEquals("foo.bar.UncoveredClass", uncoveredClassNode.qualifiedName)

      val viewExtension = bundle.coverageEngine.createCoverageViewExtension(project, bundle)
      fun assertStats(node: AbstractTreeNode<*>, vararg expected: String) {
        expected.forEachIndexed { index, value ->
          Assert.assertEquals(value, viewExtension.getPercentage(index + 1, node))
        }
      }
      assertStats(rootNode, "50% (1/2)", "40% (2/5)", "40% (2/5)", "0% (0/4)")
      assertStats(packageNode, "50% (1/2)", "40% (2/5)", "40% (2/5)", "0% (0/4)")
      assertStats(classNode, "100% (1/1)", "66% (2/3)", "66% (2/3)", "0% (0/2)")
      assertStats(subPackageNode, "0% (0/1)", "0% (0/2)", "0% (0/2)", "0% (0/2)")
      assertStats(uncoveredClassNode, "0% (0/1)", "0% (0/2)", "0% (0/2)", "0% (0/2)")
    }
    finally {
      suite?.let(manager::removeCoverageSuite)
      reportPath?.let(Files::deleteIfExists)
      coverageOptions.coverageRunner = previousRunner
      coverageViewState.isFlattenPackages = previousFlattenPackages
      coverageViewState.isHideFullyCovered = previousHideFullyCovered
      coverageViewState.isShowOnlyModified = previousShowOnlyModified
    }
  }
}
