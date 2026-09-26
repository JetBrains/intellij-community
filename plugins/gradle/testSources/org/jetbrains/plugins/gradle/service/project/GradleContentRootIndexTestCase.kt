// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.testFramework.common.mock.notImplemented
import org.jetbrains.plugins.gradle.model.ExternalProject
import java.io.File
import java.nio.file.Path

abstract class GradleContentRootIndexTestCase {

  fun createExternalProject(projectPath: Path): ExternalProject {
    return createExternalProject(projectPath, projectPath.resolve("build"))
  }

  fun createExternalProject(projectPath: Path, projectBuildPath: Path): ExternalProject {
    return MockExternalProject(projectPath, projectBuildPath)
  }

  private class MockExternalProject(
    private val projectPath: Path,
    private val buildPath: Path,
  ) : ExternalProject by notImplemented<ExternalProject>() {
    override fun getProjectDir(): File = projectPath.toFile()
    override fun getBuildDir(): File = buildPath.toFile()
  }
}