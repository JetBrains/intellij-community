// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.internal.statistic.FUCollectorTestCase
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class LifecycleUsageTriggerCollectorTest {
  private val project by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `Home project opening reports projectless`() {
    assertProjectOpened(setOf(ProjectFrameCapability.WELCOME_EXPERIENCE), projectless = true)
  }

  @Test
  fun `regular project opening reports not projectless`() {
    assertProjectOpened(emptySet(), projectless = false)
  }

  @Test
  fun `other frame capabilities do not mark a project as projectless`() {
    assertProjectOpened(setOf(ProjectFrameCapability.SUPPRESS_INDEXING_ACTIVITIES), projectless = false)
  }

  private fun assertProjectOpened(capabilities: Set<ProjectFrameCapability>, projectless: Boolean) {
    ExtensionTestUtil.maskExtensions(
      ProjectFrameCapabilitiesService.EP_NAME,
      listOf(object : ProjectFrameCapabilitiesProvider {
        override fun getCapabilities(project: Project): Set<ProjectFrameCapability> = capabilities

        override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? = null
      }),
      disposable,
    )

    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      LifecycleUsageTriggerCollector.onProjectOpened(project)
    }
    val event = events.single { it.group.id == "lifecycle" && it.event.id == "project.opened" }
    assertEquals(projectless, event.event.data["projectless"])
  }
}
