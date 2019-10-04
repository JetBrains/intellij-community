// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.action

import com.intellij.internal.statistic.actions.CleanupLocalWhitelistAction
import com.intellij.internal.statistic.eventLog.whitelist.WhitelistTestGroupStorage
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.junit.Test

class CleanupLocalWhitelistActionTest : BasePlatformTestCase() {

  @Test
  fun testCleanupLocalWhitelist() {
    val groupId = "test.group"
    val recorderId = "FUS"

    WhitelistTestGroupStorage.getInstance(recorderId).addTestGroup(groupId)
    val dataContext = getProjectContext(myFixture.project)
    val e = AnActionEvent(null, dataContext, "test", Presentation(), ActionManager.getInstance(), 0)
    CleanupLocalWhitelistAction().actionPerformed(e)

    TestCase.assertNull(
      WhitelistTestGroupStorage.getInstance(recorderId).getGroupRules(groupId)
    )
  }
}