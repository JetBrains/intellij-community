// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model

import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule.BASE_GRADLE_VERSION
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildIdentifier
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalProjectIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class DefaultGradleLightBuildTest {

  @Test
  fun `test DefaultGradleLightBuild#convertGradleBuilds`() {
    // GIVEN
    val rootBuildPath = "rootBuild"
    val rootIdentityPath = ":rootBuild"
    val gradleRootBuild = mockBuildWithProjectAndSubproject(rootBuildPath, rootIdentityPath)

    val includedBuildPath = "rootBuild/includedBuild"
    val includedIdentityPath = ":rootBuild:includedBuild"
    val gradleIncludedBuild = mockBuildWithProjectAndSubproject(includedBuildPath, includedIdentityPath, parent = gradleRootBuild)

    val deepIncludedBuildPath = "rootBuild/includedBuild/deepIncludedBuild"
    val deepIncludedIdentityPath = ":rootBuild:includedBuild:deepIncludedBuild"
    val gradleSubIncludedBuild = mockBuildWithProjectAndSubproject(deepIncludedBuildPath, deepIncludedIdentityPath, parent = gradleIncludedBuild)

    val gradleBuilds = listOf(gradleRootBuild, gradleIncludedBuild, gradleSubIncludedBuild)
    // WHEN
    val convertedBuilds = DefaultGradleLightBuild.convertGradleBuilds(gradleBuilds, GradleVersion.version(BASE_GRADLE_VERSION))
    // THEN
    assertIterableEquals(gradleBuilds.map { it.buildIdentifier.rootDir.name },
                         convertedBuilds.map { it.buildIdentifier.rootDir.name },
                         "The order of builds in `DefaultGradleLightBuild.convertGradleBuilds` should be the same as in the input")
    val rootBuild = convertedBuilds[0]
    verifyBuildRootProjectAndSubproject(rootBuild,
                                        rootBuildPath,
                                        identityPath = rootIdentityPath)
    val includedBuild = convertedBuilds[1]
    verifyBuildRootProjectAndSubproject(includedBuild,
                                        includedBuildPath,
                                        identityPath = includedIdentityPath,
                                        parentBuild = rootBuild)
    val deepIncludedBuild = convertedBuilds[2]
    verifyBuildRootProjectAndSubproject(deepIncludedBuild,
                                        deepIncludedBuildPath,
                                        identityPath = deepIncludedIdentityPath,
                                        parentBuild = includedBuild)
  }

  private fun mockBuildWithProjectAndSubproject(
    path: String,
    identityPath: String,
    parent: GradleBuild? = null,
  ): GradleBuild {
    val buildDir = File(path)
    val rootProject = mockGradleProject(name = buildDir.name,
                                        path = ":",
                                        buildTreePath = identityPath,
                                        buildDir = buildDir,
                                        projectDir = buildDir)
    val subproject = mockGradleProject(name = "subproject",
                                       path = ":subproject",
                                       buildTreePath = "$identityPath:subproject",
                                       buildDir = buildDir,
                                       projectDir = File("$path/subproject"),
                                       parent = rootProject)
    val gradleBuild = mockGradleBuild(buildDir, listOf(rootProject, subproject))
    if (parent != null) {
      whenever(parent.includedBuilds).thenReturn(ImmutableDomainObjectSet(listOf(gradleBuild)))
    }
    return gradleBuild
  }

  private fun mockGradleProject(
    name: String,
    path: String,
    buildTreePath: String,
    buildDir: File,
    projectDir: File? = null,
    parent: BasicGradleProject? = null,
  ): BasicGradleProject =
    mock<BasicGradleProject>().also {
      whenever(it.projectIdentifier).thenReturn(
        InternalProjectIdentifier(InternalBuildIdentifier(buildDir), path))
      whenever(it.name).thenReturn(name)
      whenever(it.path).thenReturn(path)
      whenever(it.projectDirectory).thenReturn(projectDir ?: buildDir)
      whenever(it.parent).thenReturn(parent)
      whenever(it.children).thenReturn(ImmutableDomainObjectSet(emptyList()))
      whenever(it.buildTreePath).thenReturn(buildTreePath)

      if (parent != null) {
        whenever(parent.children).thenReturn(ImmutableDomainObjectSet(listOf(it)))
      }
    }

  private fun mockGradleBuild(buildDir: File, projects: List<BasicGradleProject>): GradleBuild =
    mock<GradleBuild>().also {
      whenever(it.buildIdentifier).thenReturn(DefaultBuildIdentifier(buildDir))
      whenever(it.rootProject).thenReturn(projects.first())
      whenever(it.projects).thenReturn(ImmutableDomainObjectSet(projects))
      whenever(it.includedBuilds).thenReturn(ImmutableDomainObjectSet(emptyList()))
      whenever(it.editableBuilds).thenThrow(NotMockedMemberError())
    }

  private fun verifyBuildRootProjectAndSubproject(
    build: DefaultGradleLightBuild,
    buildPath: String,
    identityPath: String,
    parentBuild: DefaultGradleLightBuild? = null,
  ) {
    val buildDir = File(buildPath)
    val buildName = buildDir.name

    assertEquals(buildName, build.name)
    assertEquals(buildDir.absolutePath, build.buildIdentifier.rootDir.absolutePath)
    assertEquals(parentBuild, build.parentBuild)

    val (rootProject, subproject) = build.projects.toList()
    verifyProject(project = rootProject,
                  name = buildName,
                  path = ":",
                  identityPath = identityPath,
                  build = build,
                  projectDir = buildDir,
                  subprojects = listOf(subproject))
    verifyProject(project = subproject,
                  name = "subproject",
                  path = ":subproject",
                  identityPath = "$identityPath:subproject",
                  build = build,
                  projectDir = File("$buildPath/subproject"))
  }

  private fun verifyProject(
    project: DefaultGradleLightProject,
    name: String,
    path: String,
    identityPath: String,
    build: DefaultGradleLightBuild,
    projectDir: File,
    subprojects: List<DefaultGradleLightProject>? = emptyList(),
  ) {
    assertEquals(build, project.build)
    assertEquals(name, project.name)
    assertEquals(path, project.path)
    assertEquals(identityPath, project.identityPath)
    assertEquals(projectDir.absolutePath, project.projectDirectory.absolutePath)
    assertEquals(build.buildIdentifier.rootDir.absolutePath, project.projectIdentifier.buildIdentifier.rootDir.absolutePath)
    assertEquals(path, project.projectIdentifier.projectPath)
    assertIterableEquals(subprojects, project.childProjects)
  }
}