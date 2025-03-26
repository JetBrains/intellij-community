// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dependencyAnalyzer

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.dependency.analyzer.GradleDependencyNodeIndex
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.buildScript
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatConfigurationCacheIsSupported
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
          GradleDependencyNodeIndex.getOrCollectDependencies(project, moduleData, emptyList()).await()
        }

        assertSyncViewTree {
          assertNode("successful") {
            assertNode(":GradleDependencyReportTask")
          }
        }

        assertNonEmptyDependencyScopeNodes(expectedDependencyNodes, dependencyNodes)
      }
    }
  }

  companion object {
    private const val DEPENDENCY_COORDINATES = "org.apache.commons:commons-lang3:3.17.0"
  }
}