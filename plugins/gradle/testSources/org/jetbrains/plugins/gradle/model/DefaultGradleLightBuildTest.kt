// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model

import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier
import org.jetbrains.plugins.gradle.testFramework.util.GradleVersionSpecificsUtil.isBuildTreePathAvailable
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildIdentifier
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalProjectIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import java.io.File

class DefaultGradleLightBuildTest {

  @ParameterizedTest
  @GradleTestSource("8.1, 8.2")
  fun `test DefaultGradleLightBuild#convertGradleBuilds for simple build`(gradleVersion: GradleVersion) {
    // GIVEN
    val rootProject = mockGradleProject(
      path = ":", buildTreePath = ":",
      buildPath = "/rootBuild",
      gradleVersion = gradleVersion,
    )
    val subproject = mockGradleProject(
      path = ":subproject", buildTreePath = ":subproject",
      buildPath = "/rootBuild",
      projectPath = "/rootBuild/subproject",
      gradleVersion = gradleVersion,
      parent = rootProject
    )
    val gradleRootBuild = mockGradleBuild(
      buildPath = "/rootBuild",
      buildProjects = listOf(rootProject, subproject)
    )

    // WHEN
    val convertedBuilds = DefaultGradleLightBuild.convertGradleBuilds(listOf(gradleRootBuild), gradleVersion)

    // THEN
    assertEquals(1, convertedBuilds.size)
    val rootBuild = convertedBuilds.single()

    verifyBuild(
      build = rootBuild,
      buildPath = "/rootBuild",
      projectNames = listOf("rootBuild", "subproject"))
    verifyProject(
      project = rootBuild.projects[0],
      projectPath = "/rootBuild",
      identityPath = ":",
      build = rootBuild, path = ":",
      subprojects = listOf(rootBuild.projects[1]),
    )
    verifyProject(
      project = rootBuild.projects[1],
      identityPath = ":subproject",
      build = rootBuild, path = ":subproject",
      projectPath = "/rootBuild/subproject",
    )
  }

  @ParameterizedTest
  @GradleTestSource("8.1, 8.2")
  fun `test DefaultGradleLightBuild#convertGradleBuilds for composite build`(gradleVersion: GradleVersion) {
    // GIVEN
    val rootProject = mockGradleProject(path = ":", buildTreePath = ":", buildPath = "/rootBuild", gradleVersion = gradleVersion)
    val gradleRootBuild = mockGradleBuild(buildPath = "/rootBuild", buildProjects = listOf(rootProject))

    val includedRootProject = mockGradleProject(
      path = ":", buildTreePath = ":includedBuild",
      buildPath = "/rootBuild/includedBuild",
      gradleVersion = gradleVersion,
    )
    val includedSubproject = mockGradleProject(
      path = ":subproject", buildTreePath = ":includedBuild:subproject",
      buildPath = "/rootBuild/includedBuild",
      projectPath = "/rootBuild/includedBuild/subproject",
      gradleVersion = gradleVersion,
      parent = includedRootProject
    )
    val gradleIncludedBuild = mockGradleBuild(
      buildPath = "/rootBuild/includedBuild",
      buildProjects = listOf(includedRootProject, includedSubproject),
      parent = gradleRootBuild
    )

    val deepIncludedRootProject = mockGradleProject(
      path = ":", buildTreePath = ":includedBuild:deepIncludedBuild",
      buildPath = "/rootBuild/includedBuild/deepIncludedBuild",
      gradleVersion = gradleVersion,
    )
    val deepIncludedSubproject = mockGradleProject(
      path = ":subproject", buildTreePath = ":includedBuild:deepIncludedBuild:subproject",
      buildPath = "/rootBuild/includedBuild/deepIncludedBuild",
      projectPath = "/rootBuild/includedBuild/deepIncludedBuild/subproject",
      gradleVersion = gradleVersion,
      parent = deepIncludedRootProject
    )
    val gradleDeepIncludedBuild = mockGradleBuild(
      buildPath = "/rootBuild/includedBuild/deepIncludedBuild",
      buildProjects = listOf(deepIncludedRootProject, deepIncludedSubproject),
      parent = gradleIncludedBuild
    )

    // WHEN
    val gradleBuilds = listOf(gradleRootBuild, gradleIncludedBuild, gradleDeepIncludedBuild)
    val convertedBuilds = DefaultGradleLightBuild.convertGradleBuilds(gradleBuilds, gradleVersion)

    // THEN
    assertEquals(3, convertedBuilds.size)
    val (rootBuild, includedBuild, deepIncludedBuild) = convertedBuilds

    verifyBuild(rootBuild, "/rootBuild", listOf("rootBuild"))

    verifyBuild(
      build = includedBuild,
      buildPath = "/rootBuild/includedBuild",
      projectNames = listOf("includedBuild", "subproject"),
      parent = rootBuild,
    )
    verifyProject(
      project = includedBuild.projects[0],
      projectPath = "/rootBuild/includedBuild",
      identityPath = ":includedBuild",
      build = includedBuild, path = ":",
      subprojects = listOf(includedBuild.projects[1]),
    )
    verifyProject(
      project = includedBuild.projects[1],
      projectPath = "/rootBuild/includedBuild/subproject",
      identityPath = ":includedBuild:subproject",
      build = includedBuild, path = ":subproject",
    )

    verifyBuild(
      build = deepIncludedBuild,
      buildPath = "/rootBuild/includedBuild/deepIncludedBuild",
      projectNames = listOf("deepIncludedBuild", "subproject"),
      parent = includedBuild,
    )
    verifyProject(
      project = deepIncludedBuild.projects[0],
      projectPath = "/rootBuild/includedBuild/deepIncludedBuild",
      identityPath = ":includedBuild:deepIncludedBuild",
      build = deepIncludedBuild, path = ":",
      subprojects = listOf(deepIncludedBuild.projects[1]),
    )
    verifyProject(
      project = deepIncludedBuild.projects[1],
      projectPath = "/rootBuild/includedBuild/deepIncludedBuild/subproject",
      identityPath = ":includedBuild:deepIncludedBuild:subproject",
      build = deepIncludedBuild, path = ":subproject",
    )
  }

  private fun mockGradleProject(
    path: String,
    buildTreePath: String,
    buildPath: String,
    projectPath: String = buildPath,
    gradleVersion: GradleVersion,
    parent: BasicGradleProject? = null,
  ): BasicGradleProject = mock {
    val buildIdentifier = InternalBuildIdentifier(File(buildPath))
    on { this.projectIdentifier } doReturn InternalProjectIdentifier(buildIdentifier, path)
    on { this.name } doReturn File(projectPath).name
    on { this.path } doReturn path
    on { this.projectDirectory } doReturn File(projectPath)
    on { this.parent } doReturn parent
    on { this.children } doReturn ImmutableDomainObjectSet(emptyList())

    if (isBuildTreePathAvailable(gradleVersion)) {
      assertNotNull(buildTreePath) { "Please define buildTreePath for tests with Gradle 8.2 or newer." }
      on { this.buildTreePath } doReturn buildTreePath
    }
    else {
      on { this.buildTreePath } doThrow IllegalStateException(
        "getBuildTreePath() is not available in Gradle below 8.2, so shouldn't be called."
      )
    }

    if (parent != null) {
      on { parent.children } doReturn ImmutableDomainObjectSet(listOf(it))
    }
  }

  private fun mockGradleBuild(
    buildPath: String,
    buildProjects: List<BasicGradleProject>,
    parent: GradleBuild? = null,
  ): GradleBuild = mock {
    on { buildIdentifier } doReturn DefaultBuildIdentifier(File(buildPath))
    on { rootProject } doReturn buildProjects.first()
    on { projects } doReturn ImmutableDomainObjectSet(buildProjects)
    on { includedBuilds } doReturn ImmutableDomainObjectSet(emptyList())
    on { editableBuilds } doReturn ImmutableDomainObjectSet(emptyList())

    if (parent != null) {
      on { parent.includedBuilds } doReturn ImmutableDomainObjectSet(listOf(it))
      on { parent.editableBuilds } doReturn ImmutableDomainObjectSet(listOf(it))
    }
  }

  private fun verifyBuild(
    build: DefaultGradleLightBuild,
    buildPath: String,
    projectNames: List<String>,
    parent: DefaultGradleLightBuild? = null,
  ) {
    assertEquals(File(buildPath).name, build.name) {
      "Build name should be equal to its root project name: by default, it's the name of directory where settings.gradle(.kts) is located."
    }
    assertEquals(buildPath, build.buildIdentifier.rootDir.path) {
      "The build directory specified in the build identifier should match the given path."
    }
    assertEquals(parent, build.parentBuild) {
      "Only the root build should have `null` parent build."
    }
    assertEquals(projectNames, build.projects.map { it.name }) {
      "The build should have projects with expected names."
    }
  }

  private fun verifyProject(
    project: DefaultGradleLightProject,
    projectPath: String,
    identityPath: String,
    build: DefaultGradleLightBuild,
    path: String,
    subprojects: List<DefaultGradleLightProject>? = emptyList(),
  ) {
    assertEquals(build, project.build) {
      "The project should belong to the given `build`."
    }
    assertEquals(File(projectPath).name, project.name) {
      "The project should have expected `name`. By default, it is the name of its directory."
    }
    assertEquals(path, project.path) {
      "The project should have expected `path`. This path is relative to project's build and separated with `:`."
    }
    assertEquals(identityPath, project.identityPath) {
      "The project should have expected `identityPath`. It identifies the project, relatively to the settings file of the root build."
    }
    assertEquals(projectPath, project.projectDirectory.path) {
      "The project should be located in expected `projectDirectory`"
    }
    assertEquals(build.buildIdentifier.rootDir, project.projectIdentifier.buildIdentifier.rootDir) {
      "The `projectIdentifier` is based on the `buildIdentifier` of its build, which should have specified the expected build directory."
    }
    assertEquals(path, project.projectIdentifier.projectPath) {
      "The `projectIdentifier` should have expected `projectPath`. This path is relative to project's build and separated with `:`."
    }
    assertEquals(subprojects, project.childProjects) {
      "The project should have expected `subprojects`."
    }
  }
}