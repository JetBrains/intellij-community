// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model

import com.intellij.testFramework.common.mock.NotMockedMemberError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultGradleLightProjectTest {

  @Test
  fun `test DefaultGradleLightProject#getProjectIdentityPath`() {
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