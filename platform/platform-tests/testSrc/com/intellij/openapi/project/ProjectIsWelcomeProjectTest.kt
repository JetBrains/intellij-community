// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Checks that [Project.isWelcomeProject] follows [ProjectFrameCapability.WELCOME_EXPERIENCE] (IJPL-258957).
 */
@TestApplication
internal class ProjectIsWelcomeProjectTest {
  private val project by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `welcome experience project is welcome project`() {
    assertIsWelcomeProject(setOf(ProjectFrameCapability.WELCOME_EXPERIENCE), expected = true)
  }

  @Test
  fun `regular project is not welcome project`() {
    assertIsWelcomeProject(emptySet(), expected = false)
  }

  @Test
  fun `other frame capabilities do not mark a project as welcome project`() {
    assertIsWelcomeProject(setOf(ProjectFrameCapability.SUPPRESS_INDEXING_ACTIVITIES), expected = false)
  }

  private fun assertIsWelcomeProject(capabilities: Set<ProjectFrameCapability>, expected: Boolean) {
    ExtensionTestUtil.maskExtensions(
      ProjectFrameCapabilitiesService.EP_NAME,
      listOf(object : ProjectFrameCapabilitiesProvider {
        override fun getCapabilities(project: Project): Set<ProjectFrameCapability> = capabilities

        override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? = null
      }),
      disposable,
    )

    assertEquals(expected, project.isWelcomeProject)
  }
}
