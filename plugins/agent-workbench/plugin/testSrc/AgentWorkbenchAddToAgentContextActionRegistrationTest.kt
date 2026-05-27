// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.plugin

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchAddToAgentContextActionRegistrationTest {
  @Test
  fun registersAddToAgentContextActionInSupportedPopupGroups() {
    val actionManager = ActionManager.getInstance()

    assertNotNull(actionManager.getAction(ADD_TO_AGENT_CONTEXT_ACTION_ID))
    assertActionGroupContains(actionManager, "EditorPopupMenu")
    assertActionGroupContains(actionManager, "ProjectViewPopupMenu")
    assertActionGroupContains(actionManager, "EditorTabPopupMenu")
    assertActionGroupContains(actionManager, "ConsoleEditorPopupMenu")
    assertActionGroupContains(actionManager, "TestTreePopupMenu")
    assertActionGroupContains(actionManager, "ChangesViewPopupMenu")
    assertActionGroupContains(actionManager, "Vcs.Log.ContextMenu")
    assertActionGroupContains(actionManager, "Vcs.Log.ChangesBrowser.Popup")
  }

  private fun assertActionGroupContains(actionManager: ActionManager, groupId: String) {
    val group = actionManager.getAction(groupId)
    assertTrue(group is ActionGroup, "Action group '$groupId' is not registered")

    val childIds = (group as ActionGroup)
      .getChildren(TestActionEvent.createTestEvent())
      .mapNotNull { action -> actionManager.getId(action) }
    assertTrue(
      ADD_TO_AGENT_CONTEXT_ACTION_ID in childIds,
      "Action group '$groupId' does not contain '$ADD_TO_AGENT_CONTEXT_ACTION_ID': $childIds",
    )
  }

  private companion object {
    private const val ADD_TO_AGENT_CONTEXT_ACTION_ID = "AgentWorkbenchPrompt.AddToAgentContext"
  }
}
