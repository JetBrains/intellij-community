// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model

import com.intellij.idea.IJIgnore
import com.intellij.testFramework.common.mock.NotMockedMemberError
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import com.intellij.gradle.toolingExtension.util.GradleVersionSpecificsUtil.isBuildSrcSyncedSeparately
import com.intellij.gradle.toolingExtension.util.GradleVersionSpecificsUtil.isBuildTreePathAvailable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import java.io.File

class DefaultGradleLightProjectTest {

  @ParameterizedTest
  @GradleTestSource("8.1, 8.2")
  fun `test identity path is not calculated when buildTreePath is available`(gradleVersion: GradleVersion) {
    val build = mock<DefaultGradleLightBuild> {
      on { name } doReturn "project"
    }
    val gradleProject = mock<BasicGradleProject> {
      on { buildTreePath } doReturn "BUILD_TREE_PATH" // intentionally wrong value
      on { name } doReturn "project"
      on { path } doReturn ":"
      on { projectIdentifier } doReturn DefaultProjectIdentifier(File("project"), ":")
      on { projectDirectory } doReturn File("project")
    }

    val lightProject = DefaultGradleLightProject(build, gradleProject, gradleVersion)

    if (isBuildTreePathAvailable(gradleVersion))
      assertEquals("BUILD_TREE_PATH", lightProject.identityPath) {
        "Since Gradle 8.2, the identityPath should be taken from buildTreePath as is."
      }
    else
      assertEquals(":", lightProject.identityPath) {
        "Before Gradle 8.2, the identityPath is calculated in DefaultGradleLightProject#getProjectIdentityPath."
      }
  }

  @Test
  fun `test DefaultGradleLightProject#getProjectIdentityPath for composite build`() {
    val rootBuild = mockLightBuild("project")
    val rootProject = mockLightProject(path = ":", rootBuild)
    val rootSubproject = mockLightProject(path = ":subproject", rootBuild)

    val includedBuild = mockLightBuild("includedBuild", rootBuild)
    val includedProject = mockLightProject(path = ":", includedBuild)
    val includedSubproject = mockLightProject(path = ":subproject", includedBuild)

    val deepIncludedBuild = mockLightBuild("deepIncludedBuild", includedBuild)
    val deepIncludedProject = mockLightProject(path = ":", deepIncludedBuild)
    val deepIncludedSubproject = mockLightProject(path = ":subproject", deepIncludedBuild)

    verifyIdentityPath(":", rootProject)
    verifyIdentityPath(":subproject", rootSubproject)

    verifyIdentityPath(":includedBuild", includedProject)
    verifyIdentityPath(":includedBuild:subproject", includedSubproject)

    verifyIdentityPath(":includedBuild:deepIncludedBuild", deepIncludedProject)
    verifyIdentityPath(":includedBuild:deepIncludedBuild:subproject", deepIncludedSubproject)
  }

  /**
   * identityPath calculation for buildSrc is wrong for versions below 8.0,
   * because buildSrc build is synced separately and doesn't have a parent build
   * TODO combine tests when IDEA-375500 is fixed
   */
  @IJIgnore(issue = "IDEA-375500")
  @ParameterizedTest
  @GradleTestSource("7.6")
  fun `test identity path for buildSrc of the root build BEFORE 8,0`(gradleVersion: GradleVersion) {
    testIdentityPathForBuildSrcOfRootBuild(gradleVersion)
  }

  @ParameterizedTest
  @GradleTestSource("8.0")
  fun `test identity path for buildSrc of the root build AFTER 8,0`(gradleVersion: GradleVersion) {
    testIdentityPathForBuildSrcOfRootBuild(gradleVersion)
  }

  private fun testIdentityPathForBuildSrcOfRootBuild(gradleVersion: GradleVersion) {
    val rootBuild = mockLightBuild("project")

    val buildSrcBuild = mockLightBuild(
      buildName = "buildSrc",
      parent = when (isBuildSrcSyncedSeparately(gradleVersion)) {
        true -> null
        else -> rootBuild
      }
    )
    val buildSrcProject = mockLightProject(path = ":", buildSrcBuild)
    val buildSrcSubproject = mockLightProject(path = ":subproject", buildSrcBuild)

    verifyIdentityPath(":buildSrc", buildSrcProject)
    verifyIdentityPath(":buildSrc:subproject", buildSrcSubproject)
  }

  /**
   * identityPath calculation for buildSrc is wrong for versions below 8.0,
   * because buildSrc build is synced separately and doesn't have a parent build
   * TODO combine tests when IDEA-375500 is fixed
   */
  @IJIgnore(issue = "IDEA-375500")
  @ParameterizedTest
  @GradleTestSource("7.6")
  fun `test identity path for buildSrc of an included build BEFORE 8,0`(gradleVersion: GradleVersion) {
    testCalculationForBuildSrcOfIncluded(gradleVersion)
  }

  @ParameterizedTest
  @GradleTestSource("8.0")
  fun `test identity path for buildSrc of an included build AFTER 8,0`(gradleVersion: GradleVersion) {
    testCalculationForBuildSrcOfIncluded(gradleVersion)
  }

  private fun testCalculationForBuildSrcOfIncluded(gradleVersion: GradleVersion) {
    val rootBuild = mockLightBuild("project")
    val includedBuild = mockLightBuild("includedBuild", parent = rootBuild)

    val buildSrcOfIncludedBuild = mockLightBuild(
      buildName = "buildSrc",
      parent = when (isBuildSrcSyncedSeparately(gradleVersion)) {
        true -> null
        else -> includedBuild
      }
    )
    val buildSrcOfIncludedProject = mockLightProject(path = ":", buildSrcOfIncludedBuild)
    val buildSrcOfIncludedSubproject = mockLightProject(path = ":subproject", buildSrcOfIncludedBuild)

    verifyIdentityPath(":includedBuild:buildSrc", buildSrcOfIncludedProject)
    verifyIdentityPath(":includedBuild:buildSrc:subproject", buildSrcOfIncludedSubproject)
  }

  private fun verifyIdentityPath(identityPath: String, rootProject: GradleLightProject) {
    assertEquals(identityPath, DefaultGradleLightProject.getProjectIdentityPath(rootProject)) {
      "The identityPath, calculated by `DefaultGradleLightProject#getProjectIdentityPath` has unexpected value."
    }
  }

  private fun mockLightBuild(
    buildName: String,
    parent: GradleLightBuild? = null,
  ): GradleLightBuild = mock {
    on { this.name } doReturn buildName
    on { parentBuild } doReturn parent
    on { buildIdentifier } doThrow NotMockedMemberError()
    on { rootProject } doThrow NotMockedMemberError()
    on { projects } doThrow NotMockedMemberError()
  }

  private fun mockLightProject(
    path: String,
    build: GradleLightBuild,
  ): GradleLightProject = mock {
    on { this.build } doReturn build
    on { this.path } doReturn path
    on { name } doThrow NotMockedMemberError()
    on { projectDirectory } doThrow NotMockedMemberError()
    on { projectIdentifier } doThrow NotMockedMemberError()
    on { childProjects } doThrow NotMockedMemberError()
  }
}