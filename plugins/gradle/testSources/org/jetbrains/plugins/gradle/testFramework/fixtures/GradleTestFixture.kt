// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.project.Project
import org.gradle.util.GradleVersion
import org.jetbrains.jps.model.java.JdkVersionDetector.JdkVersionInfo
import java.nio.file.Path

interface GradleTestFixture {

  val gradleVersion: GradleVersion

  val gradleJvm: String

  val gradleJvmPath: String

  val gradleJvmInfo: JdkVersionInfo

  suspend fun openProject(projectPath: Path, numProjectSyncs: Int = 1): Project

  suspend fun linkProject(project: Project, projectPath: Path)

  suspend fun syncProject(project: Project, projectPath: Path, configure: ImportSpecBuilder.() -> Unit = {})

  suspend fun <R> withAllowedProjectSyncs(numProjectSyncs: Int = 1, action: suspend () -> R): R
}