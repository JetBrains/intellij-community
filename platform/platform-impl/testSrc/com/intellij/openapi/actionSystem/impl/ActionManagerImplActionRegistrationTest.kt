// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.openapi.actionSystem.AbbreviationManager
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
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement.ActionDescriptorAction
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.xml.dom.XmlElement
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.event.InputEvent
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
  fun failedXmlActionRegistrationDoesNotPublishPendingSideEffects() {
    val state = ActionManagerState()
    val idToAction = HashMap<String, AnAction>()
    val boundShortcuts = HashMap<String, String>()
    val registrar = ActionPreInitRegistrar(idToAction, boundShortcuts, state)
    val groupId = "ActionManagerImplActionRegistrationTest.xml.failed.group"
    val actionId = "ActionManagerImplActionRegistrationTest.xml.failed.action"
    val sourceActionId = "ActionManagerImplActionRegistrationTest.xml.failed.source"
    val abbreviation = "ActionManagerImplActionRegistrationTestXmlFailed"
    val group = DefaultActionGroup()
    val existingAction = createAction()
    val keymapToOperations = HashMap<String, MutableList<KeymapShortcutOperation>>()
    registerAction(actionId = groupId,
                   action = group,
                   pluginId = null,
                   projectType = null,
                   actionRegistrar = registrar)
    registerAction(actionId = actionId,
                   action = existingAction,
                    pluginId = null,
                    projectType = null,
                    actionRegistrar = registrar)

    val module = createActionPluginDescriptor(createXmlActionElement(actionId = actionId,
                                                                     groupId = groupId,
                                                                     sourceActionId = sourceActionId,
                                                                     abbreviation = abbreviation))
    ignoreLoggedErrors {
      ActionPluginRegistrar().registerActions(descriptors = sequenceOf(module),
                                              keymapToOperations = keymapToOperations,
                                              actionRegistrar = registrar)
    }

    assertTrue(keymapToOperations.isEmpty())
    assertNull(registrar.getActionBinding(actionId))
    assertTrue(AbbreviationManager.getInstance().getAbbreviations(actionId).isEmpty())
    assertTrue(state.getParentGroupIds(actionId).isEmpty())
    assertTrue(group.childActionsOrStubs.isEmpty())
    assertSame(existingAction, idToAction[actionId])
  }

  @Test
  fun successfulXmlActionRegistrationPublishesPendingSideEffects() {
    val state = ActionManagerState()
    val idToAction = HashMap<String, AnAction>()
    val boundShortcuts = HashMap<String, String>()
    val registrar = ActionPreInitRegistrar(idToAction, boundShortcuts, state)
    val groupId = "ActionManagerImplActionRegistrationTest.xml.success.group"
    val actionId = "ActionManagerImplActionRegistrationTest.xml.success.action"
    val sourceActionId = "ActionManagerImplActionRegistrationTest.xml.success.source"
    val abbreviation = "ActionManagerImplActionRegistrationTestXmlSuccess"
    val group = DefaultActionGroup()
    val keymapToOperations = HashMap<String, MutableList<KeymapShortcutOperation>>()
    registerAction(actionId = groupId,
                   action = group,
                   pluginId = null,
                    projectType = null,
                    actionRegistrar = registrar)
    try {
      val module = createActionPluginDescriptor(createXmlActionElement(actionId = actionId,
                                                                       groupId = groupId,
                                                                       sourceActionId = sourceActionId,
                                                                       abbreviation = abbreviation))
      ActionPluginRegistrar().registerActions(descriptors = sequenceOf(module),
                                              keymapToOperations = keymapToOperations,
                                              actionRegistrar = registrar)

      val action = idToAction[actionId]
      assertEquals(sourceActionId, registrar.getActionBinding(actionId))
      assertEquals(setOf(abbreviation), AbbreviationManager.getInstance().getAbbreviations(actionId))
      assertEquals(listOf(groupId), state.getParentGroupIds(actionId))
      assertSame(action, group.childActionsOrStubs.single())
      val operation = keymapToOperations[defaultKeymapName()]?.single()
      assertTrue(operation is AddShortcutOperation)
      assertEquals(actionId, (operation as AddShortcutOperation).actionId)
    }
    finally {
      AbbreviationManager.getInstance().removeAllAbbreviations(actionId)
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

  @Test
  fun defaultActionGroupResolvesRelativeConstraintsForActionAddedAfterSnapshot() {
    val group = DefaultActionGroup()
    val firstAction = createAction()
    val secondAction = createAction()
    val firstId = "ActionManagerImplActionRegistrationTest.snapshot.first"
    val secondId = "ActionManagerImplActionRegistrationTest.snapshot.second"
    val ids = mapOf(firstAction to firstId, secondAction to secondId)
    val secondIdResolved = CountDownLatch(1)
    val releaseSecondAdd = CountDownLatch(1)
    val failure = AtomicReference<Throwable>()
    val blockingActionManager = createLockCheckingActionManager(group, ids) { action ->
      if (action === secondAction) {
        secondIdResolved.countDown()
        assertTrue(releaseSecondAdd.await(5, TimeUnit.SECONDS))
      }
    }
    val secondAddThread = Thread({
                                   try {
                                     group.addAction(secondAction, Constraints(Anchor.BEFORE, firstId), blockingActionManager)
                                   }
                                   catch (e: Throwable) {
                                     failure.set(e)
                                   }
                                 }, "DefaultActionGroup stale snapshot add")

    secondAddThread.start()
    assertTrue(secondIdResolved.await(5, TimeUnit.SECONDS))
    group.addAction(firstAction, Constraints.LAST, createLockCheckingActionManager(group, ids))
    releaseSecondAdd.countDown()
    secondAddThread.join(5_000)

    assertFalse(secondAddThread.isAlive)
    failure.get()?.let { throw it }
    assertEquals(listOf(secondAction, firstAction), group.childActionsOrStubs.toList())
  }

  private fun createActionPluginDescriptor(actionElement: XmlElement): IdeaPluginDescriptorImpl {
    val builder = PluginDescriptorBuilder.builder()
    builder.id = "com.intellij.actionManagerImplActionRegistrationTest"
    builder.addAction(ActionDescriptorAction(className = TestAction::class.java.name,
                                            isInternal = false,
                                            element = actionElement,
                                            resourceBundle = null))
    return PluginMainDescriptor(raw = builder.build(),
                                pluginPath = Path.of("ActionManagerImplActionRegistrationTest"),
                                isBundled = true)
  }

  private fun createXmlActionElement(actionId: String, groupId: String, sourceActionId: String, abbreviation: String): XmlElement {
    return XmlElement(name = ACTION_ELEMENT_NAME,
                      attributes = mapOf(ID_ATTR_NAME to actionId,
                                         CLASS_ATTR_NAME to TestAction::class.java.name,
                                         USE_SHORTCUT_OF_ATTR_NAME to sourceActionId),
                      children = listOf(XmlElement(name = "keyboard-shortcut",
                                                   attributes = mapOf("keymap" to defaultKeymapName(), "first-keystroke" to "alt shift T")),
                                        XmlElement(name = "abbreviation", attributes = mapOf("value" to abbreviation)),
                                        XmlElement(name = ADD_TO_GROUP_ELEMENT_NAME, attributes = mapOf(GROUP_ID_ATTR_NAME to groupId))))
  }

  private fun createAction(): AnAction {
    return object : AnAction() {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

      override fun actionPerformed(e: AnActionEvent) {
      }
    }
  }

  private fun defaultKeymapName(): String = "$" + "default"

  private class TestAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
    }
  }

  private fun <T> ignoreLoggedErrors(action: () -> T): T {
    val processor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> = emptySet()
    }
    val token = LoggedErrorProcessor.executeWith(processor)
    try {
      return action()
    }
    finally {
      token.finish()
    }
  }

  private fun createLockCheckingActionManager(
    group: DefaultActionGroup,
    ids: Map<AnAction, String>,
    beforeReturnId: (AnAction) -> Unit = {},
  ): ActionManager {
    return object : ActionManager() {
      override fun createActionPopupMenu(place: String, group: ActionGroup): ActionPopupMenu = unsupported()

      override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean): ActionToolbar = unsupported()

      override fun getAction(actionId: String): AnAction? = ids.entries.firstOrNull { it.value == actionId }?.key

      override fun getId(action: AnAction): String? {
        assertFalse(Thread.holdsLock(group))
        beforeReturnId(action)
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
