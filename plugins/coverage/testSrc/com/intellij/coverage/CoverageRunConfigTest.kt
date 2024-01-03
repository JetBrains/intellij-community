// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CoverageRunConfigTest : CoverageIntegrationBaseTest() {
  @Test
  fun `test coverage run config creation`() {
    val runManager = RunManager.getInstance(project)
    val runConfig = runManager.findConfigurationByName("foo in simple")?.configuration as RunConfigurationBase<*>
    val coverageConfig = CoverageEnabledConfiguration.getOrCreate(runConfig)
    coverageConfig as JavaCoverageEnabledConfiguration

    Assert.assertFalse(coverageConfig.isBranchCoverageEnabled)
    Assert.assertFalse(coverageConfig.isTrackPerTestCoverage)
    Assert.assertFalse(coverageConfig.isTrackTestFolders)
    Assert.assertTrue(coverageConfig.coverageRunner is IDEACoverageRunner)
    val includeConfigPattens = coverageConfig.patterns!!
    Assert.assertEquals(1, includeConfigPattens.size)
    Assert.assertEquals("foo.*", includeConfigPattens[0])
    Assert.assertTrue(coverageConfig.excludePatterns.isNullOrEmpty())
    Assert.assertNull(coverageConfig.currentCoverageSuite)

    val suite = JavaCoverageEngine.getInstance().createCoverageSuite(coverageConfig.coverageRunner!!,
                                                                     coverageConfig.name,
                                                                     DefaultCoverageFileProvider(coverageConfig.coverageFilePath),
                                                                     coverageConfig)
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
}
