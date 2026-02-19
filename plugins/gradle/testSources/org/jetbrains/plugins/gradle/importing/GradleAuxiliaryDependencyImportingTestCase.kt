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

abstract class GradleAuxiliaryDependencyImportingTestCase : GradleImportingTestCase() {

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
    gradleSystemSettings.isDownloadSources = settings.ideaDownloadSourcesValue
  }

  override fun configureGradleVmOptions(options: MutableSet<String>) {
    super.configureGradleVmOptions(options)
    if (settings.forceDownloadSourcesFlagValue != null) {
      options.add("-D$FORCE_ARGUMENT_PROPERTY_NAME=${settings.forceDownloadSourcesFlagValue}")
    }
  }

  override fun tearDown() {
    runAll(
      { GradleSystemSettings.getInstance().apply { isDownloadSources = false } },
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
      coordinates, gradleUserHome, setOf(LibraryPathType.BINARY, LibraryPathType.SOURCE, LibraryPathType.DOC)
    )
    assertThat(components[LibraryPathType.BINARY]).isNotEmpty()

    if (settings.sourcesExpected) {
      assertThat(components[LibraryPathType.SOURCE]).isNotEmpty()
    }
    else {
      assertThat(components[LibraryPathType.SOURCE]).isNull()
    }

    if (settings.javadocExpected) {
      assertThat(components[LibraryPathType.DOC]).isNotEmpty()
    }
    else {
      assertThat(components[LibraryPathType.DOC]).isNull()
    }
  }

  data class TestScenario(
    val pluginDownloadSourcesValue: Boolean?,
    val ideaDownloadSourcesValue: Boolean,
    val forceDownloadSourcesFlagValue: Boolean?,
    val sourcesExpected: Boolean,
    val pluginDownloadJavadocValue: Boolean?,
    val javadocExpected: Boolean,
  )

  fun TestScenario.isIdeaPluginRequired(): Boolean = pluginDownloadJavadocValue != null || pluginDownloadSourcesValue != null

  companion object {

    private const val FORCE_ARGUMENT_PROPERTY_NAME = "idea.gradle.download.sources.force"
    private const val DEPENDENCY_CACHE_PATH = "caches/modules-2/files-2.1/junit/junit/4.12/"

    private val testCaseMatrix: List<TestScenario> = listOf(
      // sources tests
      TestScenario(
        pluginDownloadSourcesValue = true,
        ideaDownloadSourcesValue = true,
        forceDownloadSourcesFlagValue = true,
        sourcesExpected = true,
        pluginDownloadJavadocValue = false,
        javadocExpected = false
      ),
      TestScenario(
        pluginDownloadSourcesValue = true,
        ideaDownloadSourcesValue = true,
        forceDownloadSourcesFlagValue = false,
        sourcesExpected = false,
        pluginDownloadJavadocValue = false,
        javadocExpected = false
      ),
      TestScenario(
        pluginDownloadSourcesValue = true,
        ideaDownloadSourcesValue = false,
        forceDownloadSourcesFlagValue = null,
        sourcesExpected = true,
        pluginDownloadJavadocValue = false,
        javadocExpected = false
      ),
      TestScenario(
        pluginDownloadSourcesValue = false,
        ideaDownloadSourcesValue = true,
        forceDownloadSourcesFlagValue = null,
        sourcesExpected = false,
        pluginDownloadJavadocValue = false,
        javadocExpected = false
      ),
      TestScenario(
        pluginDownloadSourcesValue = false,
        ideaDownloadSourcesValue = true,
        forceDownloadSourcesFlagValue = true,
        sourcesExpected = true,
        pluginDownloadJavadocValue = false,
        javadocExpected = false
      ),
      TestScenario(
        pluginDownloadSourcesValue = null,
        ideaDownloadSourcesValue = true,
        forceDownloadSourcesFlagValue = null,
        sourcesExpected = true,
        pluginDownloadJavadocValue = false,
        javadocExpected = false
      ),
      TestScenario(
        pluginDownloadSourcesValue = null,
        ideaDownloadSourcesValue = false,
        forceDownloadSourcesFlagValue = null,
        sourcesExpected = false,
        pluginDownloadJavadocValue = false,
        javadocExpected = false
      ),

      // javadoc tests
      TestScenario(
        pluginDownloadSourcesValue = true,
        ideaDownloadSourcesValue = true,
        forceDownloadSourcesFlagValue = true,
        sourcesExpected = true,
        pluginDownloadJavadocValue = true,
        javadocExpected = true
      ),
      TestScenario(
        pluginDownloadSourcesValue = true,
        ideaDownloadSourcesValue = true,
        forceDownloadSourcesFlagValue = false,
        sourcesExpected = false,
        pluginDownloadJavadocValue = true,
        javadocExpected = true
      ),
      TestScenario(
        pluginDownloadSourcesValue = true,
        ideaDownloadSourcesValue = false,
        forceDownloadSourcesFlagValue = null,
        sourcesExpected = true,
        pluginDownloadJavadocValue = true,
        javadocExpected = true
      ),
      TestScenario(
        pluginDownloadSourcesValue = false,
        ideaDownloadSourcesValue = false,
        forceDownloadSourcesFlagValue = false,
        sourcesExpected = false,
        pluginDownloadJavadocValue = true,
        javadocExpected = true
      )
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