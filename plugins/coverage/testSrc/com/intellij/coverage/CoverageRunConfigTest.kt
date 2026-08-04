// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.classFilter.ClassFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Files
import java.nio.file.Path


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
    Assert.assertTrue(coverageConfig.coverageRunner is IDEACoverageRunner)
    val includeConfigPattens = coverageConfig.patterns!!
    Assert.assertEquals(1, includeConfigPattens.size)
    Assert.assertEquals("foo.*", includeConfigPattens[0])
    Assert.assertTrue(coverageConfig.excludePatterns.isNullOrEmpty())
    Assert.assertNull(coverageConfig.currentCoverageSuite)

    val suite = JavaCoverageEngine.getInstance().createCoverageSuite(coverageConfig)
    suite as JavaCoverageSuite

    Assert.assertTrue(suite.isBranchCoverage)
    Assert.assertFalse(suite.isCoverageByTestEnabled)
    Assert.assertTrue(suite.isCoverageByTestApplicable)
    Assert.assertFalse(suite.isTrackTestFolders)
    Assert.assertTrue(suite.runner is IDEACoverageRunner)
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
