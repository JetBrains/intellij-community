// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class ProjectlessDataTest {
  private val project by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `null project is null`() {
    assertThat(ProjectlessData.forProject(null)).isEqualTo(null)
  }

  @Test
  fun `a project with a frame of its own is not projectless`() {
    maskCapabilities(emptySet())
    assertThat(ProjectlessData.forProject(project)).isEqualTo(EventFields.Projectless.with(false))
  }

  @Test
  fun `the welcome project is projectless`() {
    maskCapabilities(setOf(ProjectFrameCapability.WELCOME_EXPERIENCE))
    assertThat(ProjectlessData.forProject(project)).isEqualTo(EventFields.Projectless.with(true))
  }

  @Test
  fun `a project loaded without a frame is projectless`() {
    maskCapabilities(setOf(ProjectFrameCapability.NO_FRAME_OF_ITS_OWN))
    assertThat(ProjectlessData.forProject(project)).isEqualTo(EventFields.Projectless.with(true))
  }

  private fun maskCapabilities(capabilities: Set<ProjectFrameCapability>) {
    val provider = object : ProjectFrameCapabilitiesProvider {
      override fun getCapabilities(project: Project): Set<ProjectFrameCapability> = capabilities

      override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? = null
    }
    ExtensionTestUtil.maskExtensions(ProjectFrameCapabilitiesService.EP_NAME, listOf(provider), disposable)
  }
}
