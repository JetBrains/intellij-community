// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.storage.GroupValidationTestRule
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationTestRulesPersistedStorage
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.junit.Test

class CleanupEventsTestSchemeActionTest : BasePlatformTestCase() {

  @Test
  fun testCleanupEventsTestScheme() {
    val groupId = "test.group"
    val recorderId = "FUS"

    IntellijSensitiveDataValidator.getInstance(recorderId)
    ValidationTestRulesPersistedStorage.getTestStorage(recorderId, true)!!.addTestGroup(GroupValidationTestRule("groupId", false))
    val dataContext = SimpleDataContext.getProjectContext(myFixture.project)
    val e = AnActionEvent(null, dataContext, "test", Presentation(), ActionManager.getInstance(), 0)
    CleanupEventsTestSchemeAction(recorderId).actionPerformed(e)

    TestCase.assertNull(
      ValidationTestRulesPersistedStorage.getTestStorage(recorderId, true)!!.getGroupRules(groupId)
    )
  }
}