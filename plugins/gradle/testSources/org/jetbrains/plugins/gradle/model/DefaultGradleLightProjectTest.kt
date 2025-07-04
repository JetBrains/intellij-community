// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model

import com.intellij.testFramework.common.mock.NotMockedMemberError
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.jetbrains.plugins.gradle.testFramework.util.GradleVersionSpecificsUtil.isBuildSrcSyncedSeparately
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock

class DefaultGradleLightProjectTest {

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

  @ParameterizedTest
  @GradleTestSource("7.6, 8.0")
  fun `test DefaultGradleLightProject#getProjectIdentityPath for buildSrc`(gradleVersion: GradleVersion) {
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

  // TODO support or create a YouTrack issue for this case
  @Disabled("identityPath calculation for buildSrc of included build is not supported for 7.6 version, " +
            "because buildSrc build is synced separately and doesn't have a parent build")
  @ParameterizedTest
  @GradleTestSource("7.6, 8.0")
  fun `test DefaultGradleLightProject#getProjectIdentityPath for buildSrc of included build`(gradleVersion: GradleVersion) {
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