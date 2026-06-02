// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Anchor
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.TimerListener
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.ActionCallback
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.event.InputEvent

@TestApplication
@Suppress("UnstableApiUsage")
internal class ActionManagerImplActionRegistrationTest {
  @Test
  fun runtimeRegistrarAddToGroupRecordsGroupMapping() {
    val actionManager = ActionManager.getInstance() as ActionManagerImpl
    val registrar = ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar()
    val groupId = "ActionManagerImplActionRegistrationTest.add.group"
    val childId = "ActionManagerImplActionRegistrationTest.add.child"
    val group = DefaultActionGroup()
    val action = createAction()
    actionManager.registerAction(groupId, group)
    actionManager.registerAction(childId, action)
    try {
      registrar.addToGroup(group, action, Constraints.LAST)

      assertEquals(listOf(groupId), actionManager.groupIds(childId))
      assertSame(action, group.childActionsOrStubs.single())
    }
    finally {
      actionManager.unregisterAction(childId)
      actionManager.unregisterAction(groupId)
    }
  }

  @Test
  fun runtimeRegistrarAddToGroupResolvesRelativeConstraintsFromSnapshot() {
    val actionManager = ActionManager.getInstance() as ActionManagerImpl
    val registrar = ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar()
    val groupId = "ActionManagerImplActionRegistrationTest.relative.group"
    val firstId = "ActionManagerImplActionRegistrationTest.relative.first"
    val secondId = "ActionManagerImplActionRegistrationTest.relative.second"
    val group = DefaultActionGroup()
    val firstAction = createAction()
    val secondAction = createAction()
    actionManager.registerAction(groupId, group)
    actionManager.registerAction(firstId, firstAction)
    actionManager.registerAction(secondId, secondAction)
    try {
      registrar.addToGroup(group, firstAction, Constraints.LAST)
      registrar.addToGroup(group, secondAction, Constraints(Anchor.BEFORE, firstId))

      assertEquals(listOf(secondAction, firstAction), group.childActionsOrStubs.toList())
      assertEquals(listOf(groupId), actionManager.groupIds(firstId))
      assertEquals(listOf(groupId), actionManager.groupIds(secondId))
    }
    finally {
      actionManager.unregisterAction(secondId)
      actionManager.unregisterAction(firstId)
      actionManager.unregisterAction(groupId)
    }
  }

  @Test
  fun replaceActionPreservesGroupMapping() {
    val actionManager = ActionManager.getInstance() as ActionManagerImpl
    val registrar = ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar()
    val groupId = "ActionManagerImplActionRegistrationTest.replace.group"
    val childId = "ActionManagerImplActionRegistrationTest.replace.child"
    val group = DefaultActionGroup()
    val originalAction = createAction()
    val replacementAction = createAction()
    actionManager.registerAction(groupId, group)
    actionManager.registerAction(childId, originalAction)
    try {
      registrar.addToGroup(group, originalAction, Constraints.LAST)

      actionManager.replaceAction(childId, replacementAction)

      assertEquals(listOf(groupId), actionManager.groupIds(childId))
      assertSame(replacementAction, group.childActionsOrStubs.single())
    }
    finally {
      actionManager.unregisterAction(childId)
      actionManager.unregisterAction(groupId)
    }
  }

  @Test
  fun failedReplaceWithAlreadyRegisteredActionKeepsOriginalGroupState() {
    val actionManager = ActionManager.getInstance() as ActionManagerImpl
    val registrar = ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar()
    val groupId = "ActionManagerImplActionRegistrationTest.failed.replace.group"
    val childId = "ActionManagerImplActionRegistrationTest.failed.replace.child"
    val replacementId = "ActionManagerImplActionRegistrationTest.failed.replace.replacement"
    val group = DefaultActionGroup()
    val originalAction = createAction()
    val replacementAction = createAction()
    actionManager.registerAction(groupId, group)
    actionManager.registerAction(childId, originalAction)
    actionManager.registerAction(replacementId, replacementAction)
    try {
      registrar.addToGroup(group, originalAction, Constraints.LAST)

      ignoreLoggedErrors {
        actionManager.replaceAction(childId, replacementAction)
      }

      assertSame(originalAction, actionManager.getAction(childId))
      assertSame(replacementAction, actionManager.getAction(replacementId))
      assertEquals(listOf(groupId), actionManager.groupIds(childId))
      assertTrue(actionManager.groupIds(replacementId).isEmpty())
      assertSame(originalAction, group.childActionsOrStubs.single())
    }
    finally {
      actionManager.unregisterAction(replacementId)
      actionManager.unregisterAction(childId)
      actionManager.unregisterAction(groupId)
    }
  }

  @Test
  fun failedDuplicateActionRegistrationDoesNotLeaveStaleState() {
    val actionManager = ActionManager.getInstance() as ActionManagerImpl
    val registrar = ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar()
    val groupId = "ActionManagerImplActionRegistrationTest.duplicate.group"
    val childId = "ActionManagerImplActionRegistrationTest.duplicate.child"
    val duplicateId = "ActionManagerImplActionRegistrationTest.duplicate.duplicate"
    val group = DefaultActionGroup()
    val action = createAction()
    actionManager.registerAction(groupId, group)
    actionManager.registerAction(childId, action)
    try {
      registrar.addToGroup(group, action, Constraints.LAST)

      ignoreLoggedErrors {
        actionManager.registerAction(duplicateId, action)
      }

      assertSame(action, actionManager.getAction(childId))
      assertEquals(childId, actionManager.getId(action))
      assertNull(actionManager.getAction(duplicateId))
      assertEquals(listOf(groupId), actionManager.groupIds(childId))
      assertTrue(actionManager.groupIds(duplicateId).isEmpty())
      assertSame(action, group.childActionsOrStubs.single())
    }
    finally {
      actionManager.unregisterAction(duplicateId)
      actionManager.unregisterAction(childId)
      actionManager.unregisterAction(groupId)
    }
  }

  @Test
  fun unregisterGroupRemovesGroupMappingFromChildren() {
    val actionManager = ActionManager.getInstance() as ActionManagerImpl
    val registrar = ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar()
    val groupId = "ActionManagerImplActionRegistrationTest.unregister.group"
    val childId = "ActionManagerImplActionRegistrationTest.unregister.child"
    val group = DefaultActionGroup()
    val action = createAction()
    actionManager.registerAction(groupId, group)
    actionManager.registerAction(childId, action)
    try {
      registrar.addToGroup(group, action, Constraints.LAST)

      actionManager.unregisterAction(groupId)

      assertTrue(actionManager.groupIds(childId).isEmpty())
    }
    finally {
      actionManager.unregisterAction(childId)
      actionManager.unregisterAction(groupId)
    }
  }

  @Test
  fun defaultActionGroupDoesNotCallActionManagerResolverUnderGroupMonitor() {
    val group = DefaultActionGroup()
    val firstAction = createAction()
    val secondAction = createAction()
    val firstId = "ActionManagerImplActionRegistrationTest.monitor.first"
    val secondId = "ActionManagerImplActionRegistrationTest.monitor.second"
    val actionManager = createLockCheckingActionManager(group, mapOf(firstAction to firstId, secondAction to secondId))

    group.addAction(firstAction, Constraints.LAST, actionManager)
    group.addAction(secondAction, Constraints(Anchor.AFTER, firstId), actionManager)

    assertEquals(listOf(firstAction, secondAction), group.childActionsOrStubs.toList())
  }

  private fun createAction(): AnAction {
    return object : AnAction() {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

      override fun actionPerformed(e: AnActionEvent) {
      }
    }
  }

  private fun ignoreLoggedErrors(action: () -> Unit) {
    val processor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> = emptySet()
    }
    LoggedErrorProcessor.executeWith<Throwable>(processor) { action() }
  }

  private fun createLockCheckingActionManager(group: DefaultActionGroup, ids: Map<AnAction, String>): ActionManager {
    return object : ActionManager() {
      override fun createActionPopupMenu(place: String, group: ActionGroup): ActionPopupMenu = unsupported()

      override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean): ActionToolbar = unsupported()

      override fun getAction(actionId: String): AnAction? = ids.entries.firstOrNull { it.value == actionId }?.key

      override fun getId(action: AnAction): String? {
        assertFalse(Thread.holdsLock(group))
        return ids[action]
      }

      override fun registerAction(actionId: String, action: AnAction) {
      }

      override fun registerAction(actionId: String, action: AnAction, pluginId: PluginId?) {
      }

      override fun unregisterAction(actionId: String) {
      }

      override fun replaceAction(actionId: String, newAction: AnAction) {
      }

      @Suppress("OVERRIDE_DEPRECATION")
      override fun getActionIds(idPrefix: String): Array<String> = emptyArray()

      override fun getActionIdList(idPrefix: String): List<String> = emptyList()

      override fun isGroup(actionId: String): Boolean = false

      override fun getActionOrStub(id: String): AnAction? = getAction(id)

      override fun addTimerListener(listener: TimerListener) {
      }

      override fun removeTimerListener(listener: TimerListener) {
      }

      override fun tryToExecute(
        action: AnAction,
        inputEvent: InputEvent?,
        contextComponent: Component?,
        place: String?,
        now: Boolean,
      ): ActionCallback = ActionCallback.REJECTED

      override fun getKeyboardShortcut(actionId: String): KeyboardShortcut? = null
    }
  }

  private fun unsupported(): Nothing = throw UnsupportedOperationException()
}
