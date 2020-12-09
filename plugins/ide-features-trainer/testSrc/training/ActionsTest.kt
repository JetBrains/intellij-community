// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.LightPlatformTestCase

class ActionsTest : LightPlatformTestCase() {
  fun testActions() {
    assertFalse(actionExists("com.intellij.ide.projectView.impl.ProjectViewImpl\$ManualOrderAction"))
    assertTrue(actionExists("com.intellij.ui.content.tabs.TabbedContentAction\$MyNextTabAction"))
    assertTrue(actionExists("com.intellij.ide.todo.SetTodoFilterAction\$1"))
    assertTrue(actionExists("com.intellij.codeInsight.daemon.impl.DaemonTooltipWithActionRenderer\$addActionsRow\$1"))
    assertTrue(actionExists("com.intellij.execution.testframework.ToolbarPanel\$SortByDurationAction"))
  }

  private fun actionExists(id: String): Boolean {
    return try {
      Class.forName(id)
      true
    } catch (e: Exception) {
      ActionManager.getInstance().getAction(id) != null
    }
  }
}