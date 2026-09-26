// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import java.awt.Dimension
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestApplication
internal class ActionPresentationShortcutTextTest {
  @Test
  @RunMethodInEdt
  fun registeredActionShortcutTextDoesNotReadActionShortcutSet() {
    val localAction = object : AnAction("Local") {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }
    localAction.setShortcutSet(CustomShortcutSet.fromString("control X"))
    assertTrue(KeymapUtil.getFirstKeyboardShortcutText(localAction).isNotEmpty())

    val actionId = "ActionPresentationShortcutTextTest.Action"
    val action = object : AnAction("Test") {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }
    val actionManager = ActionManager.getInstance()
    actionManager.registerAction(actionId, action)
    try {
      action.setShortcutSet(ShortcutSet { throw AssertionError("Registered action shortcutSet should not be queried for display") })

      assertEquals("", KeymapUtil.getFirstKeyboardShortcutText(action))

      val button = ActionButton(action, null, "Test", Dimension())
      button.update()
    }
    finally {
      actionManager.unregisterAction(actionId)
    }
  }
}
