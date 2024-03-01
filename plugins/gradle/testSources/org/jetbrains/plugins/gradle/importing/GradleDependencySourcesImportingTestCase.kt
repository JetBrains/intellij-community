// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.utils.io.deleteRecursively
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.service.cache.GradleLocalCacheHelper
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.junit.Assume
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter

abstract class GradleDependencySourcesImportingTestCase : GradleImportingTestCase() {

  @Parameter(1)
  lateinit var settings: TestScenario

  override fun setUp() {
    Assume.assumeFalse(
      "Can not run on Windows. The test locks native library files in test directory and can not be torn down properly.",
      SystemInfo.isWindows
    )

    super.setUp()

    gradleUserHome.resolve(DEPENDENCY_CACHE_PATH)
      .deleteRecursively()

    val gradleSystemSettings = GradleSystemSettings.getInstance()
    gradleSystemSettings.gradleVmOptions = null
    gradleSystemSettings.isDownloadSources = settings.ideaSettingsValue
    if (settings.forceFlagValue != null) {
      gradleSystemSettings.gradleVmOptions = "-D$FORCE_ARGUMENT_PROPERTY_NAME=${settings.forceFlagValue}"
    }
  }

  override fun tearDown() {
    runAll(
      { GradleSystemSettings.getInstance().apply { isDownloadSources = false; gradleVmOptions = null } },
      { super.tearDown() }
    )
  }

  fun assertSingleLibraryOrderEntry(moduleName: String, dependencyName: String): LibraryOrderEntry {
    val moduleLibDeps = getModuleLibDeps(moduleName, dependencyName)
    assertThat(moduleLibDeps).hasSize(1)
    return moduleLibDeps.single()
  }

  fun assertDependencyInGradleCache(dependency: String) {
    val (groupId, artifactId, version) = dependency.split(":")
    val coordinates = UnifiedCoordinates(groupId, artifactId, version)
    val components = GradleLocalCacheHelper.findArtifactComponents(
      coordinates, gradleUserHome, setOf(LibraryPathType.BINARY, LibraryPathType.SOURCE)
    )
    assertThat(components[LibraryPathType.BINARY]).isNotEmpty()
    if (settings.sourcesExpected) {
      assertThat(components[LibraryPathType.SOURCE]).isNotEmpty()
    }
    else {
      assertThat(components[LibraryPathType.SOURCE]).isNull()
    }
  }

  data class TestScenario(
    val ideaPluginValue: Boolean?,
    val ideaSettingsValue: Boolean,
    val forceFlagValue: Boolean?,
    val sourcesExpected: Boolean
  )

  companion object {

    private const val FORCE_ARGUMENT_PROPERTY_NAME = "idea.gradle.download.sources.force"
    private const val DEPENDENCY_CACHE_PATH = "caches/modules-2/files-2.1/junit/junit/4.12/"

    private val testCaseMatrix: List<TestScenario> = listOf(
      TestScenario(ideaPluginValue = true, ideaSettingsValue = true, forceFlagValue = true, sourcesExpected = true),
      TestScenario(ideaPluginValue = true, ideaSettingsValue = true, forceFlagValue = false, sourcesExpected = false),
      TestScenario(ideaPluginValue = true, ideaSettingsValue = false, forceFlagValue = null, sourcesExpected = true),
      TestScenario(ideaPluginValue = false, ideaSettingsValue = true, forceFlagValue = null, sourcesExpected = false),
      TestScenario(ideaPluginValue = false, ideaSettingsValue = true, forceFlagValue = true, sourcesExpected = true),
      TestScenario(ideaPluginValue = null, ideaSettingsValue = true, forceFlagValue = null, sourcesExpected = true),
      TestScenario(ideaPluginValue = null, ideaSettingsValue = false, forceFlagValue = null, sourcesExpected = false),
    )

    @JvmStatic
    @Parameterized.Parameters(name = "gradleVersion={0}, settings={1}")
    fun data(): List<Array<Any>> {
      val result = ArrayList<Array<Any>>()
      val supportedVersions = VersionMatcherRule.getSupportedGradleVersions()
      for (version in supportedVersions.takeLast(3)) {
        for (testCase in testCaseMatrix) {
          result.add(arrayOf(version, testCase))
        }
      }
      return result
    }
  }
}