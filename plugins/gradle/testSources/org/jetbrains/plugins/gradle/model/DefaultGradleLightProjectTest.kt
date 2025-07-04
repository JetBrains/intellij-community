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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultGradleLightProjectTest {

  @Test
  fun `test DefaultGradleLightProject#getProjectIdentityPath for composite build`() {
    val rootBuild = mockLightBuild("project")
    val rootProject = mockLightProject(":", rootBuild)
    val rootSubproject = mockLightProject(":subproject", rootBuild)

    val includedBuild = mockLightBuild("includedBuild", rootBuild)
    val includedProject = mockLightProject(":", includedBuild)
    val includedSubproject = mockLightProject(":subproject", includedBuild)

    val deepIncludedBuild = mockLightBuild("deepIncludedBuild", includedBuild)
    val deepIncludedProject = mockLightProject(":", deepIncludedBuild)
    val deepIncludedSubproject = mockLightProject(":subproject", deepIncludedBuild)

    assertEquals(":", DefaultGradleLightProject.getProjectIdentityPath(rootProject))
    assertEquals(":subproject", DefaultGradleLightProject.getProjectIdentityPath(rootSubproject))

    assertEquals(":includedBuild", DefaultGradleLightProject.getProjectIdentityPath(includedProject))
    assertEquals(":includedBuild:subproject", DefaultGradleLightProject.getProjectIdentityPath(includedSubproject))

    assertEquals(":includedBuild:deepIncludedBuild", DefaultGradleLightProject.getProjectIdentityPath(deepIncludedProject))
    assertEquals(":includedBuild:deepIncludedBuild:subproject",
                            DefaultGradleLightProject.getProjectIdentityPath(deepIncludedSubproject))
  }

  @ParameterizedTest
  @GradleTestSource("7.6, 8.0")
  fun `test DefaultGradleLightProject#getProjectIdentityPath for buildSrc`(gradleVersion: GradleVersion) {
    val rootBuild = mockLightBuild("project")
    val rootProject = mockLightProject(path = ":", rootBuild)

    val buildSrcBuild = mockLightBuild(
      name = "buildSrc",
      parent = when (isBuildSrcSyncedSeparately(gradleVersion)) {
        true -> null
        else -> rootBuild
      }
    )
    val buildSrcProject = mockLightProject(path = ":", buildSrcBuild)
    val buildSrcSubproject = mockLightProject(path = ":subproject", buildSrcBuild)

    assertEquals(":", DefaultGradleLightProject.getProjectIdentityPath(rootProject))

    assertEquals(":buildSrc", DefaultGradleLightProject.getProjectIdentityPath(buildSrcProject))
    assertEquals(":buildSrc:subproject", DefaultGradleLightProject.getProjectIdentityPath(buildSrcSubproject))
  }

  // TODO support or create a YouTrack issue for this case
  @Disabled("identityPath calculation for buildSrc of included build is not supported for 7.6 version, " +
            "because buildSrc build is synced separately and doesn't have a parent build")
  @ParameterizedTest
  @GradleTestSource("7.6, 8.0")
  fun `test DefaultGradleLightProject#getProjectIdentityPath for buildSrc of included build`(gradleVersion: GradleVersion) {
    val rootBuild = mockLightBuild("project")
    val rootProject = mockLightProject(path = ":", rootBuild)

    val includedBuild = mockLightBuild("includedBuild", parent = rootBuild)
    val includedProject = mockLightProject(path = ":", includedBuild)

    val buildSrcOfIncludedBuild = mockLightBuild(
      name = "buildSrc",
      parent = when (isBuildSrcSyncedSeparately(gradleVersion)) {
        true -> null
        else -> includedBuild
      }
    )
    val buildSrcOfIncludedProject = mockLightProject(path = ":", buildSrcOfIncludedBuild)
    val buildSrcOfIncludedSubproject = mockLightProject(path = ":subproject", buildSrcOfIncludedBuild)

    assertEquals(":", DefaultGradleLightProject.getProjectIdentityPath(rootProject))

    assertEquals(":includedBuild", DefaultGradleLightProject.getProjectIdentityPath(includedProject))

    assertEquals(":includedBuild:buildSrc", DefaultGradleLightProject.getProjectIdentityPath(buildSrcOfIncludedProject))
    assertEquals(":includedBuild:buildSrc:subproject", DefaultGradleLightProject.getProjectIdentityPath(buildSrcOfIncludedSubproject))
  }

  private fun mockLightBuild(name: String, parent: GradleLightBuild? = null): GradleLightBuild =
    mock<GradleLightBuild>().also {
      whenever(it.name).thenReturn(name)
      whenever(it.parentBuild).thenReturn(parent)
      whenever(it.buildIdentifier).thenThrow(NotMockedMemberError())
      whenever(it.rootProject).thenThrow(NotMockedMemberError())
      whenever(it.projects).thenThrow(NotMockedMemberError())
    }

  private fun mockLightProject(path: String, build: GradleLightBuild): GradleLightProject =
    mock<GradleLightProject>().also {
      whenever(it.build).thenReturn(build)
      whenever(it.path).thenReturn(path)
      whenever(it.name).thenThrow(NotMockedMemberError())
      whenever(it.projectDirectory).thenThrow(NotMockedMemberError())
      whenever(it.projectIdentifier).thenThrow(NotMockedMemberError())
      whenever(it.childProjects).thenThrow(NotMockedMemberError())
    }
}