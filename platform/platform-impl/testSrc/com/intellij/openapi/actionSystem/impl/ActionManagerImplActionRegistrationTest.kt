// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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

  private fun createAction(): AnAction {
    return object : AnAction() {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

      override fun actionPerformed(e: AnActionEvent) {
      }
    }
  }
}
