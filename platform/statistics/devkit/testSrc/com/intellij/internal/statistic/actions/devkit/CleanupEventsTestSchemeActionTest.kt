// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.actions.devkit

import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.storage.GroupValidationTestRule
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationTestRulesPersistedStorage
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.platform.statistics.devkit.actions.CleanupEventsTestSchemeAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class CleanupEventsTestSchemeActionTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project get() = projectFixture.get()

  @Test
  fun testCleanupEventsTestScheme(): Unit = timeoutRunBlocking {
    val groupId = "test.group"
    val recorderId = "FUS"

    IntellijSensitiveDataValidator.getInstance(recorderId)
    ValidationTestRulesPersistedStorage.getTestStorage(recorderId, true)!!.addTestGroup(GroupValidationTestRule("groupId", false))
    val dataContext = SimpleDataContext.getProjectContext(project)
    val e = AnActionEvent(null, dataContext, "test", Presentation(), ActionManager.getInstance(), 0)
    withContext(Dispatchers.EDT) {
      CleanupEventsTestSchemeAction(recorderId).actionPerformed(e)
    }

    assertNull(
      ValidationTestRulesPersistedStorage.getTestStorage(recorderId, true)!!.getGroupRules(groupId)
    )
  }
}