// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator
import com.intellij.internal.statistic.eventLog.whitelist.LocalWhitelistGroup
import com.intellij.internal.statistic.eventLog.whitelist.WhitelistTestGroupStorage
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.junit.Test

class CleanupEventsTestSchemeActionTest : BasePlatformTestCase() {

  @Test
  fun testCleanupEventsTestScheme() {
    val groupId = "test.group"
    val recorderId = "FUS"

    SensitiveDataValidator.getInstance(recorderId)
    WhitelistTestGroupStorage.getTestStorage(recorderId)!!.addTestGroup(LocalWhitelistGroup("groupId", false))
    val dataContext = getProjectContext(myFixture.project)
    val e = AnActionEvent(null, dataContext, "test", Presentation(), ActionManager.getInstance(), 0)
    CleanupEventsTestSchemeAction(recorderId).actionPerformed(e)

    TestCase.assertNull(
      WhitelistTestGroupStorage.getTestStorage(recorderId)!!.getGroupRules(groupId)
    )
  }
}