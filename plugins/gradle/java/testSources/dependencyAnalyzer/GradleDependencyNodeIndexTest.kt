// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dependencyAnalyzer

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.dependency.analyzer.GradleDependencyNodeIndex
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.buildScript
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatConfigurationCacheIsSupported
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatIsolatedProjectsIsSupported
import org.junit.jupiter.params.ParameterizedTest

class GradleDependencyNodeIndexTest : GradleDependencyNodeIndexTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test collecting dependency nodes`(gradleVersion: GradleVersion) {
    testCollectingDependencyNodes(gradleVersion, "")
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test collecting dependency nodes with configuration cache`(gradleVersion: GradleVersion) {
    assumeThatConfigurationCacheIsSupported(gradleVersion)
    testCollectingDependencyNodes(gradleVersion, "org.gradle.configuration-cache=true")
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test collecting dependency nodes with isolated projects`(gradleVersion: GradleVersion) {
    assumeThatIsolatedProjectsIsSupported(gradleVersion)
    testCollectingDependencyNodes(gradleVersion, "org.gradle.unsafe.isolated-projects=true")
  }

  private fun testCollectingDependencyNodes(gradleVersion: GradleVersion, gradleProperties: String) {
    testEmptyProject(gradleVersion) {
      runBlocking {
        writeText("gradle.properties", gradleProperties)
        writeText("build.gradle.kts", buildScript(gradleVersion, GradleDsl.KOTLIN) {
          withJavaPlugin()
          withMavenCentral()
          addImplementationDependency(DEPENDENCY_COORDINATES)
        })

        val expectedDependencyNodes = buildList {
          add("compileClasspath" to DEPENDENCY_COORDINATES)
          add("runtimeClasspath" to DEPENDENCY_COORDINATES)
          add("testCompileClasspath" to DEPENDENCY_COORDINATES)
          add("testRuntimeClasspath" to DEPENDENCY_COORDINATES)
          if (GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.0")) {
            add("default" to DEPENDENCY_COORDINATES)
          }
        }

        val moduleData = getGradleModuleData(moduleName = project.name)

        val dependencyNodes = executionFixture.assertAnyGradleTaskExecutionAsync(numExec = 1) {
          GradleDependencyNodeIndex.getOrCollectDependencies(project, moduleData).await()
        }

        assertSyncViewTree {
          assertNode("successful") {
            assertNode(":ijCollectDependencies")
          }
        }
        assertSyncViewNode("successful") { consoleText ->
          if ("0 problems were found storing the configuration cache" !in consoleText) {
            Assertions.assertThat(consoleText)
              .doesNotContain("problem was found storing the configuration cache")
              .doesNotContain("problems were found storing the configuration cache")
          }
        }

        assertNonEmptyDependencyScopeNodes(expectedDependencyNodes, dependencyNodes)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test collecting dependency nodes with IDE cache`(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      runBlocking {
        writeText("build.gradle.kts", buildScript(gradleVersion, GradleDsl.KOTLIN) {
          withJavaPlugin()
          withMavenCentral()
          addImplementationDependency(DEPENDENCY_COORDINATES)
        })

        val expectedDependencyNodes = buildList {
          add("compileClasspath" to DEPENDENCY_COORDINATES)
          add("runtimeClasspath" to DEPENDENCY_COORDINATES)
          add("testCompileClasspath" to DEPENDENCY_COORDINATES)
          add("testRuntimeClasspath" to DEPENDENCY_COORDINATES)
          if (GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.0")) {
            add("default" to DEPENDENCY_COORDINATES)
          }
        }

        val moduleData = getGradleModuleData(moduleName = project.name)

        val dependencyNodes1 = executionFixture.assertAnyGradleTaskExecutionAsync(numExec = 1) { // expected cache miss
          GradleDependencyNodeIndex.getOrCollectDependencies(project, moduleData).await()
        }
        assertNonEmptyDependencyScopeNodes(expectedDependencyNodes, dependencyNodes1)

        val dependencyNodes2 = executionFixture.assertAnyGradleTaskExecutionAsync(numExec = 0) { // expected cache hit
          GradleDependencyNodeIndex.getOrCollectDependencies(project, moduleData).await()
        }
        assertNonEmptyDependencyScopeNodes(expectedDependencyNodes, dependencyNodes2)
      }
    }
  }

  companion object {
    private const val DEPENDENCY_COORDINATES = "org.apache.commons:commons-lang3:3.17.0"
  }
}