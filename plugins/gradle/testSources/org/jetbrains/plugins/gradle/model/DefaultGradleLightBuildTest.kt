// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model

import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildIdentifier
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalProjectIdentifier
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class DefaultGradleLightBuildTest {

  @Test
  fun `test DefaultGradleLightBuild#convertGradleBuilds`() {
    // GIVEN
    val rootBuildPath = "rootBuild"
    val includedBuildPath = "rootBuild/includedBuild"
    val subIncludedBuildPath = "rootBuild/includedBuild/subIncludedBuild"

    val gradleRootBuild = mockBuildWithProjectAndSubproject(rootBuildPath)
    val gradleIncludedBuild = mockBuildWithProjectAndSubproject(includedBuildPath, parent = gradleRootBuild)
    val gradleSubIncludedBuild = mockBuildWithProjectAndSubproject(subIncludedBuildPath, parent = gradleIncludedBuild)
    val gradleBuilds = listOf(gradleRootBuild, gradleIncludedBuild, gradleSubIncludedBuild)
    // WHEN
    val convertedBuilds = DefaultGradleLightBuild.convertGradleBuilds(gradleBuilds)
    // THEN
    Assertions.assertIterableEquals(gradleBuilds.map { it.buildIdentifier.rootDir.name },
                                    convertedBuilds.map { it.buildIdentifier.rootDir.name },
                                    "The order of builds in `DefaultGradleLightBuild.convertGradleBuilds` should be the same as in the input")
    val rootBuild = convertedBuilds[0]
    verifyBuildRootProjectAndSubproject(rootBuild, rootBuildPath)

    val includedBuild = convertedBuilds[1]
    verifyBuildRootProjectAndSubproject(includedBuild, includedBuildPath, parentBuild = rootBuild)

    val subIncluded = convertedBuilds[2]
    verifyBuildRootProjectAndSubproject(subIncluded, subIncludedBuildPath, parentBuild = includedBuild)
  }

  private fun mockBuildWithProjectAndSubproject(path: String, parent: GradleBuild? = null): GradleBuild {
    val buildDir = File(path)
    val rootProject = mockGradleProject(name = buildDir.name,
                                        path = ":",
                                        buildDir = buildDir,
                                        projectDir = buildDir)
    val subproject = mockGradleProject(name = "subproject",
                                       path = ":subproject",
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
      whenever(it.buildTreePath).thenThrow(NotMockedMemberError::class.java)

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
      whenever(it.editableBuilds).thenThrow(NotMockedMemberError::class.java)
    }

  private fun verifyBuildRootProjectAndSubproject(
    build: DefaultGradleLightBuild,
    buildPath: String,
    parentBuild: DefaultGradleLightBuild? = null,
  ) {
    val buildDir = File(buildPath)
    val buildName = buildDir.name

    Assertions.assertEquals(buildName, build.name)
    Assertions.assertEquals(buildDir.absolutePath, build.buildIdentifier.rootDir.absolutePath)
    Assertions.assertEquals(parentBuild, build.parentBuild)

    Assertions.assertEquals(2, build.projects.count())
    // The order of elements in `DefaultGradleLightBuild.getProjects` is random
    val rootProject = build.projects.find { it.name == buildName }
      .also { Assertions.assertEquals(build.rootProject, it) }!!
    val subproject = build.projects.find { it.name == "subproject" }
      .also { assertNotNull(it) }!!

    verifyProject(project = rootProject,
                  name = buildName,
                  path = ":",
                  build = build,
                  projectDir = buildDir,
                  subprojects = listOf(subproject))
    verifyProject(project = subproject,
                  name = "subproject",
                  path = ":subproject",
                  build = build,
                  projectDir = File("$buildPath/subproject"))
  }

  private fun verifyProject(
    project: DefaultGradleLightProject,
    name: String,
    path: String,
    build: DefaultGradleLightBuild,
    projectDir: File,
    subprojects: List<DefaultGradleLightProject>? = emptyList(),
  ) {
    Assertions.assertEquals(build, project.build)
    Assertions.assertEquals(name, project.name)
    Assertions.assertEquals(path, project.path)
    Assertions.assertEquals(projectDir.absolutePath, project.projectDirectory.absolutePath)
    Assertions.assertEquals(build.buildIdentifier.rootDir.absolutePath, project.projectIdentifier.buildIdentifier.rootDir.absolutePath)
    Assertions.assertEquals(path, project.projectIdentifier.projectPath)
    Assertions.assertIterableEquals(subprojects, project.childProjects)
  }

  private class NotMockedMemberError : IllegalStateException("The accessed member is not mocked. Please mock it in the test.")
}