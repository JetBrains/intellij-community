// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.gradle.util.GradleVersion
import org.jetbrains.jps.model.java.JdkVersionDetector.JdkVersionInfo
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfoAssertions.assertProjectState
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfoBuilder
import org.jetbrains.plugins.gradle.testFramework.projectInfo.complexJavaProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.deleteProject
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.initProject
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleJavaProjectInfo
import java.nio.file.Path

@TestApplication
abstract class GradleTestCase {

  val gradleVersion: GradleVersion = GradleVersion.current()

  val testPath: Path by tempPathFixture()

  private val gradleFixture by gradleFixture(gradleVersion)
  val gradleJvm: String get() = gradleFixture.gradleJvm
  val gradleJvmInfo: JdkVersionInfo get() = gradleFixture.gradleJvmInfo

  suspend fun openProject(relativePath: String, numProjectSyncs: Int = 1): Project {
    return gradleFixture.openProject(testPath.resolve(relativePath), numProjectSyncs)
  }

  suspend fun linkProject(project: Project, relativePath: String) {
    gradleFixture.linkProject(project, testPath.resolve(relativePath))
  }

  suspend fun syncProject(project: Project, relativePath: String, configure: ImportSpecBuilder.() -> Unit = {}) {
    gradleFixture.syncProject(project, testPath.resolve(relativePath), configure)
  }

  suspend fun <R> withAllowedProjectSyncs(numProjectSyncs: Int = 1, action: suspend () -> R): R {
    return gradleFixture.withAllowedProjectSyncs(numProjectSyncs, action)
  }

  suspend fun initProject(projectInfo: GradleProjectInfo): Path =
    projectInfo.initProject(testPath)

  suspend fun deleteProject(projectInfo: GradleProjectInfo): Unit =
    projectInfo.deleteProject(testPath)

  fun assertProjectState(project: Project, vararg projectsInfo: GradleProjectInfo): Unit =
    assertProjectState(project, testPath, *projectsInfo)

  fun projectInfo(
    relativePath: String,
    gradleDsl: GradleDsl = GradleDsl.KOTLIN,
    configure: GradleProjectInfoBuilder.() -> Unit = {},
  ): GradleProjectInfo =
    gradleProjectInfo(gradleVersion, relativePath, gradleDsl, configure)

  fun simpleJavaProjectInfo(relativePath: String, gradleDsl: GradleDsl = GradleDsl.KOTLIN): GradleProjectInfo =
    simpleJavaProjectInfo(gradleVersion, relativePath, gradleDsl)

  fun complexJavaProjectInfo(relativePath: String, gradleDsl: GradleDsl = GradleDsl.KOTLIN): GradleProjectInfo =
    complexJavaProjectInfo(gradleVersion, relativePath, gradleDsl)
}