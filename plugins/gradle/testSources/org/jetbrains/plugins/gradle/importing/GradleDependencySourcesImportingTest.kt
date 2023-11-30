// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.utils.io.deleteRecursively
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.service.cache.GradleLocalCacheHelper
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule.SUPPORTED_GRADLE_VERSIONS
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter

@RunWith(Parameterized::class)
class GradleDependencySourcesImportingTest : GradleImportingTestCase() {

  @Parameter(1)
  lateinit var settings: TestScenario

  data class TestScenario(val ideaPluginValue: Boolean?,
                          val ideaSettingsValue: Boolean,
                          val forceFlagValue: Boolean?,
                          val sourcesExpected: Boolean)

  companion object {

    private const val FORCE_ARGUMENT_PROPERTY_NAME = "idea.gradle.download.sources.force"

    private const val DEPENDENCY = "junit:junit:4.12"
    private const val DEPENDENCY_NAME = "Gradle: $DEPENDENCY"
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
    fun data(): List<Array<Any>> = generateParameters()

    private fun generateParameters(): List<Array<Any>> {
      val result: MutableList<Array<Any>> = ArrayList()
      val supportedVersionCount = SUPPORTED_GRADLE_VERSIONS.size
      val supportedVersions =  if (supportedVersionCount > 3) {
        SUPPORTED_GRADLE_VERSIONS.copyOfRange(supportedVersionCount - 3, supportedVersionCount)
      } else {
        SUPPORTED_GRADLE_VERSIONS
      }
      for (version in supportedVersions) {
        for (testCase in testCaseMatrix) {
          result.add(arrayOf(version, testCase))
        }
      }
      return result
    }
  }

  override fun setUp() {
    super.setUp()
    removeCachedLibrary()
  }

  @Test
  fun testDependencyPoliciesWorksWithGenericProjects() {
    executeTest {
      importProject(script {
        it
          .withJavaPlugin()
          .withIdeaPlugin()
          .withMavenCentral()
          .addTestImplementationDependency(DEPENDENCY)
        if (settings.ideaPluginValue != null) {
          it.addPrefix(
            "idea.module {",
            "  downloadJavadoc = false",
            "  downloadSources = ${settings.ideaPluginValue}",
            "}")
        }
      })
      assertSingleLibraryOrderEntry("project.test")
    }
  }

  @Test
  fun testSourcesExcludedFromGradleMultiModuleProjectCacheOnDisabledFlag() {
    executeTest {
      createSettingsFile("include 'projectA', 'projectB' ")
      val scriptBuilder = createBuildScriptBuilder()
        .withIdeaPlugin()
        .applyPlugin("idea")
        .project(":projectA") { project ->
          project
            .withJavaPlugin()
            .withMavenCentral()
            .addTestImplementationDependency(DEPENDENCY)
        }
        .project(":projectB") { project ->
          project
            .withJavaPlugin()
            .withMavenCentral()
            .addTestImplementationDependency(DEPENDENCY)
        }
      if (settings.ideaPluginValue != null) {
        scriptBuilder.addPrefix("idea.module.downloadSources = ${settings.ideaPluginValue}")
      }
      importProject(scriptBuilder.generate())
      assertSingleLibraryOrderEntry("project.projectA.test")
      assertSingleLibraryOrderEntry("project.projectB.test")
    }
  }

  private fun executeTest(test: Runnable) {
    Assume.assumeFalse("Can not run on Windows. " +
                       "The test locks native library files in test directory and can not be torn down properly.", SystemInfo.isWindows)
    GradleSettings.getInstance(myProject).isDownloadSources = settings.ideaSettingsValue
    if (settings.forceFlagValue != null) {
      GradleSystemSettings.getInstance().setGradleVmOptions("-D$FORCE_ARGUMENT_PROPERTY_NAME=${settings.forceFlagValue}")
    }
    test.run()
    assertDependencyInGradleCache(settings.sourcesExpected)
  }

  private fun removeCachedLibrary() = gradleUserHome.resolve(DEPENDENCY_CACHE_PATH).run {
    deleteRecursively()
  }

  private fun assertSingleLibraryOrderEntry(moduleName: String): LibraryOrderEntry {
    val moduleLibDeps = getModuleLibDeps(moduleName, DEPENDENCY_NAME)
    assertThat(moduleLibDeps).hasSize(1)
    return moduleLibDeps.iterator().next()
  }

  private fun assertDependencyInGradleCache(sourcesExpected: Boolean) = DEPENDENCY.split(":").let {
    val coordinates = UnifiedCoordinates(it[0], it[1], it[2])
    val components = GradleLocalCacheHelper.findArtifactComponents(coordinates,
                                                                   gradleUserHome,
                                                                   setOf(LibraryPathType.BINARY, LibraryPathType.SOURCE))
    assertThat(components[LibraryPathType.BINARY]).isNotEmpty
    if (sourcesExpected) {
      assertThat(components[LibraryPathType.SOURCE]).isNotEmpty
    }
    else {
      assertThat(components[LibraryPathType.SOURCE]).isNull()
    }
  }
}