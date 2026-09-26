// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.openapi.keymap.impl.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.QuickList
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@RunInEdt(allMethods = false)
@TestApplication
internal class ActionTreeGroupUtilTest {
  @Test
  @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.True)
  fun `create main group skips unexpected other-group children`() {
    val actionManager = ActionManagerEx.getInstanceEx()
    val originalOtherGroup = actionManager.getAction(OTHER_KEYMAP_GROUP_ID) as? DefaultActionGroup
                             ?: error("$OTHER_KEYMAP_GROUP_ID is not a DefaultActionGroup")
    val replacementOtherGroup = DefaultActionGroup()
    val alphaGroup = DefaultActionGroup(OTHER_GROUP_ALPHA_NAME, true)
    val omegaGroup = DefaultActionGroup(OTHER_GROUP_OMEGA_NAME, true)
    val alphaAction = TestAction("alpha action")
    val omegaAction = TestAction("omega action")
    val plainAction = TestAction("plain action")
    val loggedErrors = ArrayList<String>()

    try {
      actionManager.unregisterAction(OTHER_KEYMAP_GROUP_ID)
      actionManager.registerAction(OTHER_KEYMAP_GROUP_ID, replacementOtherGroup)
      actionManager.registerAction(OTHER_GROUP_ALPHA_ID, alphaGroup)
      actionManager.registerAction(OTHER_GROUP_ALPHA_ACTION_ID, alphaAction)
      actionManager.registerAction(OTHER_GROUP_OMEGA_ID, omegaGroup)
      actionManager.registerAction(OTHER_GROUP_OMEGA_ACTION_ID, omegaAction)
      actionManager.registerAction(OTHER_GROUP_PLAIN_ACTION_ID, plainAction)
      alphaGroup.add(alphaAction)
      omegaGroup.add(omegaAction)
      replacementOtherGroup.add(alphaGroup)
      replacementOtherGroup.add(plainAction)
      replacementOtherGroup.add(omegaGroup)

      LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<out String?>, t: Throwable?): Set<Action> {
          if (category == ACTION_TREE_GROUP_UTIL_LOG_CATEGORY) {
            loggedErrors.add(message)
            return Action.NONE
          }
          return super.processError(category, message, details, t)
        }
      }).use {
        val mainGroup = ActionTreeGroupUtil.createMainGroup(null, null, emptyArray<QuickList>())
        val otherGroup = requireNotNull(findGroup(mainGroup, KeyMapBundle.message("other.group.title")))
        assertThat(otherGroup.children.indexOf(OTHER_GROUP_PLAIN_ACTION_ID)).isEqualTo(1)
      }

      assertThat(loggedErrors).hasSize(1)
      assertThat(loggedErrors.single())
        .contains("Unexpected non-group children")
        .contains(OTHER_GROUP_PLAIN_ACTION_ID)
        .contains(String::class.java.name)
    }
    finally {
      replacementOtherGroup.remove(alphaGroup)
      replacementOtherGroup.remove(plainAction)
      replacementOtherGroup.remove(omegaGroup)
      unregisterIfPresent(actionManager, OTHER_GROUP_ALPHA_ACTION_ID)
      unregisterIfPresent(actionManager, OTHER_GROUP_ALPHA_ID)
      unregisterIfPresent(actionManager, OTHER_GROUP_OMEGA_ACTION_ID)
      unregisterIfPresent(actionManager, OTHER_GROUP_OMEGA_ID)
      unregisterIfPresent(actionManager, OTHER_GROUP_PLAIN_ACTION_ID)
      if (actionManager.getAction(OTHER_KEYMAP_GROUP_ID) === replacementOtherGroup) {
        actionManager.unregisterAction(OTHER_KEYMAP_GROUP_ID)
      }
      if (actionManager.getAction(OTHER_KEYMAP_GROUP_ID) == null) {
        actionManager.registerAction(OTHER_KEYMAP_GROUP_ID, originalOtherGroup)
      }
    }
  }

  private fun findGroup(parent: Group, groupName: String): Group? {
    for (child in parent.children) {
      if (child is Group && child.name == groupName) {
        return child
      }
    }
    return null
  }

  private fun unregisterIfPresent(actionManager: ActionManagerEx, actionId: String) {
    if (actionManager.getAction(actionId) != null) {
      actionManager.unregisterAction(actionId)
    }
  }

  private class TestAction(text: String) : AnAction(text) {
    override fun actionPerformed(e: AnActionEvent) {
    }
  }

  companion object {
    private const val ACTION_TREE_GROUP_UTIL_LOG_CATEGORY = "#com.intellij.openapi.keymap.impl.ui.ActionTreeGroupUtil"
    private const val OTHER_KEYMAP_GROUP_ID = "Other.KeymapGroup"
    private const val OTHER_GROUP_ALPHA_ID = "DummyOtherGroupAlpha"
    private const val OTHER_GROUP_ALPHA_ACTION_ID = "DummyOtherGroupAlphaAction"
    private const val OTHER_GROUP_ALPHA_NAME = "AAA Dummy Other Group"
    private const val OTHER_GROUP_OMEGA_ID = "DummyOtherGroupOmega"
    private const val OTHER_GROUP_OMEGA_ACTION_ID = "DummyOtherGroupOmegaAction"
    private const val OTHER_GROUP_OMEGA_NAME = "ZZZ Dummy Other Group"
    private const val OTHER_GROUP_PLAIN_ACTION_ID = "DummyOtherPlainAction"
  }
}
