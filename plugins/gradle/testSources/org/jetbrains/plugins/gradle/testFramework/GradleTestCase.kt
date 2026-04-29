// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfoAssertions.assertProjectState
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfoBuilder
import org.jetbrains.plugins.gradle.testFramework.projectInfo.complexJavaProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.deleteProject
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.initProject
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleJavaProjectInfo
import java.nio.file.Path

abstract class GradleTestCase : GradleBaseTestCase() {

  suspend fun initProject(projectInfo: GradleProjectInfo): Path =
    initProject(testPath, projectInfo)

  suspend fun deleteProject(projectInfo: GradleProjectInfo): Unit =
    deleteProject(testPath, projectInfo)

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