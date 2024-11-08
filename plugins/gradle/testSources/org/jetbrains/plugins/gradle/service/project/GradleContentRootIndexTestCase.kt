// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType
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

  fun createSources(vararg sources: Path): Map<out IExternalSystemSourceType, Collection<Path>> {
    return sources.associate { MockSourceType() to listOf(it) }
  }

  private class MockSourceType : IExternalSystemSourceType {
    override fun isTest() = throw UnsupportedOperationException()
    override fun isGenerated() = throw UnsupportedOperationException()
    override fun isResource() = throw UnsupportedOperationException()
    override fun isExcluded() = throw UnsupportedOperationException()
  }

  private class MockExternalProject(
    private val projectPath: Path,
    private val buildPath: Path,
  ) : ExternalProject {
    override fun getProjectDir(): File = projectPath.toFile()
    override fun getBuildDir(): File = buildPath.toFile()

    override fun getExternalSystemId() = throw UnsupportedOperationException()
    override fun getId() = throw UnsupportedOperationException()
    override fun getPath() = throw UnsupportedOperationException()
    override fun getIdentityPath() = throw UnsupportedOperationException()
    override fun getName() = throw UnsupportedOperationException()
    override fun getQName() = throw UnsupportedOperationException()
    override fun getDescription() = throw UnsupportedOperationException()
    override fun getGroup() = throw UnsupportedOperationException()
    override fun getVersion() = throw UnsupportedOperationException()
    override fun getSourceCompatibility() = throw UnsupportedOperationException()
    override fun getTargetCompatibility() = throw UnsupportedOperationException()
    override fun getChildProjects() = throw UnsupportedOperationException()
    override fun getBuildFile() = throw UnsupportedOperationException()
    override fun getTasks() = throw UnsupportedOperationException()
    override fun getSourceSets() = throw UnsupportedOperationException()
    override fun getArtifacts() = throw UnsupportedOperationException()
    override fun getArtifactsByConfiguration() = throw UnsupportedOperationException()
    override fun getSourceSetModel() = throw UnsupportedOperationException()
    override fun getTaskModel() = throw UnsupportedOperationException()
  }
}