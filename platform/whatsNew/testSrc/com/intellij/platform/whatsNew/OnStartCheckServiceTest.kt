// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.whatsNew.reaction.FUSReactionChecker
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

@TestApplication
class OnStartCheckServiceTest {

  private val project = projectFixture()

  private val mockContent = object : WhatsNewContent() {
    override suspend fun show(project: Project, dataContext: DataContext?, triggeredByUser: Boolean, reactionChecker: FUSReactionChecker) {
      error("Mock object, do not call")
    }
    override fun getVersion() = ContentVersion("2025", "1.1", null, "123")
    override suspend fun isAvailable() = true
  }

  @Test
  fun `WhatsNew should be disabled`() {
    val (environment, isCalled) = mockAccessor(isDisabled = true)
    val service = WhatsNewShowOnStartCheckService(environment)
    runBlocking {
      service.execute(project.get())
      assertFalse(isCalled.get(), "What's New page should not be shown")
    }
  }

  @Test
  fun `WhatsNew should be shown`() {
    val (environment, isCalled) = mockAccessor(isDisabled = false)
    val service = WhatsNewShowOnStartCheckService(environment)
    runBlocking {
      service.execute(project.get())
      assertTrue(isCalled.get(), "What's New page should be shown")
    }
  }

  private fun mockAccessor(isDisabled: Boolean): Pair<WhatsNewEnvironmentAccessor, AtomicBoolean> {
    val isCalled = AtomicBoolean(false)
    val environment = object : WhatsNewEnvironmentAccessor {
      override val isForceDisabled = isDisabled
      override suspend fun getWhatsNewContent() = mockContent
      override fun findAction() = WhatsNewAction()
      override suspend fun showWhatsNew(project: Project, action: WhatsNewAction) {
        isCalled.set(true)
      }
      override fun isDefaultWhatsNewEnabledAndReadyToShow() = false
    }

    return environment to isCalled
  }
}
