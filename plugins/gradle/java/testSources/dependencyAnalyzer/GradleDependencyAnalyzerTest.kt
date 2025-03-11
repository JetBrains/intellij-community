// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dependencyAnalyzer

import org.assertj.core.api.Assertions
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.dependency.analyzer.GradleDependencyAnalyzerContributor
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.buildScript
import org.jetbrains.plugins.gradle.testFramework.GradleExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixture.GradleExecutionOutputFixture.Companion.transform
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatConfigurationCacheIsSupported
import org.junit.jupiter.params.ParameterizedTest

class GradleDependencyAnalyzerTest : GradleExecutionTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test dependency report task with configuration cache`(gradleVersion: GradleVersion) {
    assumeThatConfigurationCacheIsSupported(gradleVersion)
    testEmptyProject(gradleVersion) {
      writeText("gradle.properties", "org.gradle.configuration-cache=true")
      writeText("build.gradle.kts", buildScript(gradleVersion, GradleDsl.KOTLIN) {
        withJavaPlugin()
        withMavenCentral()
        addImplementationDependency(DEPENDENCY_COORDINATES)
      })

      val daContributor = GradleDependencyAnalyzerContributor(project)
      val daProjects = daContributor.getProjects()

      Assertions.assertThat(daProjects)
        .transform { it.module.name }
        .containsExactlyInAnyOrder("empty-project")

      val daProject = daProjects.single()
      val daDependencies = waitForAnyGradleTaskExecution {
        daContributor.getDependencies(daProject)
      }

      assertSyncViewTree {
        assertNode("successful") {
          assertNode(":GradleDependencyReportTask")
        }
      }

      Assertions.assertThat(daDependencies)
        .transform { it.scope.name to it.data.toString() }
        .containsExactlyInAnyOrder(
          "default" to "empty-project",
          "compileClasspath" to DEPENDENCY_COORDINATES,
          "runtimeClasspath" to DEPENDENCY_COORDINATES,
          "testCompileClasspath" to DEPENDENCY_COORDINATES,
          "testRuntimeClasspath" to DEPENDENCY_COORDINATES,
        )
    }
  }

  companion object {
    private const val DEPENDENCY_COORDINATES = "org.apache.commons:commons-lang3:3.17.0"
  }
}