// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.SystemPropertyClassLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.connection.GradleConnectorService.Companion.USE_PRODUCTION_TTL_FOR_TESTS_KEY
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleProjectRootFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.projectFixture
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfoAssertions.assertProjectStructure
import org.jetbrains.plugins.gradle.testFramework.projectInfo.file
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleWrapper
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleJavaModuleInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleSettingsFile
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.EnumSource

@TestApplication
@PerformanceUnitTest
@ParameterizedClass
@BaseGradleVersionSource
class GradleJavaSyncPerformanceTest(private val gradleVersion: GradleVersion) {

  private val gradleFixture = gradleFixture(gradleVersion)
  private val gradle by gradleFixture

  @PerformanceUnitTest
  @Nested
  // The benchmark measures repeated syncProject calls, so each attempt must reuse the warm Gradle daemon.
  // IDE-side sync processing after a Tooling API call can take longer than the test TTL before the next attempt starts.
  // If the daemon expires in that gap, the next re-sync measurement includes daemon startup cost.
  @SystemPropertyClassLevel(USE_PRODUCTION_TTL_FOR_TESTS_KEY, "true")
  @ParameterizedClass
  @EnumSource(TestProjectParameters::class)
  inner class ReSync(private val projectParameters: TestProjectParameters) {

    private val projectInfo = projectParameters.projectInfo(gradleVersion)
    private val projectRootFixture = gradleProjectRootFixture(projectInfo)
    private val projectRoot by projectRootFixture

    private val projectFixture = gradleFixture.projectFixture(projectRootFixture)
    private val project by projectFixture

    private fun setup() {
      assertProjectStructure(project, projectInfo)
    }

    private fun attempt() {
      runBlocking { gradle.syncProject(project, projectRoot) }
    }

    @Test
    fun test() {
      Benchmark.newBenchmark("Gradle sync ($gradleVersion, $projectParameters)", ::attempt)
        .setup(::setup)
        .attempts(10)
        .runAsStressTest()
        .start()
    }
  }

  enum class TestProjectParameters(
    private val numHolderModules: Int,
  ) {
    SIMPLE_PROJECT(1000);

    fun projectInfo(gradleVersion: GradleVersion): GradleProjectInfo =
      gradleProjectInfo(gradleVersion) {
        gradleWrapper()
        file("gradle.properties", """
          |org.gradle.jvmargs=-Xmx3g
        """.trimMargin())
        simpleSettingsFile {
          addCode {
            call("repeat", int(numHolderModules)) {
              call("include", $$"module-$it")
            }
          }
        }
        for (i in 0 until numHolderModules) {
          simpleJavaModuleInfo("${projectName}.module-$i", "module-$i")
        }
      }
  }
}
