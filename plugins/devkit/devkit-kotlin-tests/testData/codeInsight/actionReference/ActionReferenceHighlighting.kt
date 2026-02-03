@file:Suppress("MISSING_DEPENDENCY_SUPERCLASS") // avoid test library deps

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.EmptyAction
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.AbbreviationManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter
import com.intellij.idea.ActionsBundle

import com.intellij.testFramework.fixtures.EditorTestFixture
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.PlatformTestUtil

import java.awt.Component
import java.awt.event.KeyEvent

object ActionReferenceHighlighting {

  const val ACTION_ID = "<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>"

  fun testActionManager(actionManager: ActionManager) {
    actionManager.getAction("myAction")
    actionManager.getAction("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    actionManager.registerAction("myAction", EmptyAction())
    actionManager.registerAction("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>", EmptyAction())

    actionManager.unregisterAction("myAction")
    actionManager.unregisterAction("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    actionManager.replaceAction("myAction", EmptyAction())
    actionManager.replaceAction("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>", EmptyAction())

    actionManager.isGroup("myAction")
    actionManager.isGroup("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    actionManager.getActionOrStub("myAction")
    actionManager.getActionOrStub("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    actionManager.getKeyboardShortcut("myAction")
    actionManager.getKeyboardShortcut("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")
  }

  fun testActionRuntimeRegistrar(actionRuntimeRegistrar: ActionRuntimeRegistrar) {
    actionRuntimeRegistrar.registerAction("myAction", EmptyAction())
    actionRuntimeRegistrar.registerAction("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>", EmptyAction())

    actionRuntimeRegistrar.unregisterAction("myAction")
    actionRuntimeRegistrar.unregisterAction("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    actionRuntimeRegistrar.getActionOrStub("myAction")
    actionRuntimeRegistrar.getActionOrStub("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    actionRuntimeRegistrar.getUnstubbedAction("myAction")
    actionRuntimeRegistrar.getUnstubbedAction("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    actionRuntimeRegistrar.replaceAction("myAction", EmptyAction())
    actionRuntimeRegistrar.replaceAction("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>", EmptyAction())
  }

  @Suppress("DEPRECATION")
  fun testActionUtil(component: Component) {
    ActionUtil.getUnavailableMessage("myAction", false)
    ActionUtil.getUnavailableMessage("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>", false)

    ActionUtil.getActionUnavailableMessage("myAction")
    ActionUtil.getActionUnavailableMessage("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    ActionUtil.copyFrom(EmptyAction(), "myAction")
    ActionUtil.copyFrom(EmptyAction(), "<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    ActionUtil.mergeFrom(EmptyAction(), "myAction")
    ActionUtil.mergeFrom(EmptyAction(), "<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    ActionUtil.createActionListener("myAction", component, "place")
    ActionUtil.createActionListener("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>", component, "place")

    ActionUtil.wrap("myAction")
    ActionUtil.wrap("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    ActionUtil.getShortcutSet("myAction")
    ActionUtil.getShortcutSet("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    ActionUtil.getAction("myAction")
    ActionUtil.getAction("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    ActionUtil.getActionGroup("myGroup")
    ActionUtil.getActionGroup("<error descr="Cannot resolve group 'myAction'">myAction</error>")
  }

  fun testKeymap(keymap: Keymap, shortcut: Shortcut, mouseShortcut: MouseShortcut, keyboardShortcut: KeyboardShortcut) {
    keymap.getShortcuts("myAction")
    keymap.getShortcuts("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    keymap.addShortcut("myAction", shortcut)
    keymap.addShortcut("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>", shortcut)

    keymap.removeShortcut("myAction", shortcut)
    keymap.removeShortcut("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>", shortcut)

    keymap.getConflicts("myAction", keyboardShortcut)
    keymap.getConflicts("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>", keyboardShortcut)

    keymap.removeAllActionShortcuts("myAction")
    keymap.removeAllActionShortcuts("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    keymap.hasActionId("myAction", mouseShortcut)
    keymap.hasActionId("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>", mouseShortcut)
  }

  @Suppress("MISSING_DEPENDENCY_CLASS")
  fun testKeymapUtil(keymapManager: KeymapManager, keyEvent: KeyEvent, keymap: Keymap) {
    KeymapUtil.getShortcutText("myAction")
    KeymapUtil.getShortcutText("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    KeymapUtil.getShortcutTextOrNull("myAction")
    KeymapUtil.getShortcutTextOrNull("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    KeymapUtil.getActiveKeymapShortcuts("myAction")
    KeymapUtil.getActiveKeymapShortcuts("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    KeymapUtil.getActiveKeymapShortcuts("myAction", keymapManager)
    KeymapUtil.getActiveKeymapShortcuts("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>", keymapManager)

    KeymapUtil.getPrimaryShortcut("myAction")
    KeymapUtil.getPrimaryShortcut("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    KeymapUtil.getFirstKeyboardShortcutText("myAction")
    KeymapUtil.getFirstKeyboardShortcutText("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    KeymapUtil.getFirstMouseShortcutText("myAction")
    KeymapUtil.getFirstMouseShortcutText("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    KeymapUtil.isEventForAction(keyEvent, "myAction")
    KeymapUtil.isEventForAction(keyEvent, "<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    KeymapUtil.createTooltipText("tooltip", "myAction")
    KeymapUtil.createTooltipText("tooltip", "<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    KeymapUtil.matchActionMouseShortcutsModifiers(keymap, 0, "myAction")
    KeymapUtil.matchActionMouseShortcutsModifiers(keymap, 0, "<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")
  }

  @Suppress("MISSING_DEPENDENCY_SUPERCLASS")
  fun testEditorNotificationPanel(editorNotificationPanel: EditorNotificationPanel) {
     editorNotificationPanel.createActionLabel("text", "myAction")
     editorNotificationPanel.createActionLabel("text", "<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")
  }

  fun testAbbreviationManager(abbreviationManager: AbbreviationManager) {
    abbreviationManager.getAbbreviations("myAction")
    abbreviationManager.getAbbreviations("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    abbreviationManager.removeAllAbbreviations("myAction")
    abbreviationManager.removeAllAbbreviations("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    abbreviationManager.register("text", "myAction")
    abbreviationManager.register("text", "<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    abbreviationManager.remove("text", "myAction")
    abbreviationManager.remove("text", "<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")
  }

  fun testEditorActionManager(editorActionManager : EditorActionManager, handler : com.intellij.openapi.editor.actionSystem.EditorActionHandler) {
    editorActionManager.getActionHandler("myAction")
    editorActionManager.getActionHandler("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")

    editorActionManager.setActionHandler("myAction", handler)
    editorActionManager.setActionHandler("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>", handler)
  }

  object MyEditorEmptyTextPainter : EditorEmptyTextPainter() {
    fun test() {
      getActionShortcutText("myAction")
      getActionShortcutText("<error descr="Cannot resolve action or group 'INVALID_VALUE'">INVALID_VALUE</error>")
    }
  }

  fun testActionsBundle() {
    ActionsBundle.actionText("myAction")
    ActionsBundle.actionText("<error descr="Cannot resolve action 'myGroup'">myGroup</error>")
    ActionsBundle.actionText("<error descr="Cannot resolve action 'INVALID_VALUE'">INVALID_VALUE</error>")

    ActionsBundle.actionDescription("myAction")
    ActionsBundle.actionDescription("<error descr="Cannot resolve action 'myGroup'">myGroup</error>")
    ActionsBundle.actionDescription("<error descr="Cannot resolve action 'INVALID_VALUE'">INVALID_VALUE</error>")

    ActionsBundle.groupText("myGroup")
    ActionsBundle.groupText("<error descr="Cannot resolve group 'myAction'">myAction</error>")
    ActionsBundle.groupText("<error descr="Cannot resolve group 'INVALID_VALUE'">INVALID_VALUE</error>")
  }

  fun testEditorTestFixture(editorTestFixture: EditorTestFixture) {
    editorTestFixture.performEditorAction("myAction")
    editorTestFixture.performEditorAction("<error descr="Cannot resolve action 'myGroup'">myGroup</error>")
    editorTestFixture.performEditorAction("<error descr="Cannot resolve action 'INVALID_VALUE'">INVALID_VALUE</error>")
  }

  fun testCodeInsightTestFixture(codeInsightTestFixture: CodeInsightTestFixture) {
    codeInsightTestFixture.performEditorAction("myAction")
    codeInsightTestFixture.performEditorAction("<error descr="Cannot resolve action 'myGroup'">myGroup</error>")
    codeInsightTestFixture.performEditorAction("<error descr="Cannot resolve action 'INVALID_VALUE'">INVALID_VALUE</error>")
  }

  fun testEditorTestUtil(editor : com.intellij.openapi.editor.Editor) {
    EditorTestUtil.executeAction(editor, "myAction")
    EditorTestUtil.executeAction(editor, "<error descr="Cannot resolve action 'myGroup'">myGroup</error>")
    EditorTestUtil.executeAction(editor, "<error descr="Cannot resolve action 'INVALID_VALUE'">INVALID_VALUE</error>")
  }

  object MyLightPlatformCodeInsightTestCase : LightPlatformCodeInsightTestCase() {
    fun test() {
      executeAction("myAction")
      executeAction("<error descr="Cannot resolve action 'myGroup'">myGroup</error>")
      executeAction("<error descr="Cannot resolve action 'INVALID_VALUE'">INVALID_VALUE</error>")
    }
  }

  fun testPlatformTestUtil() {
    PlatformTestUtil.invokeNamedAction("myAction")
    PlatformTestUtil.invokeNamedAction("<error descr="Cannot resolve action 'myGroup'">myGroup</error>")
    PlatformTestUtil.invokeNamedAction("<error descr="Cannot resolve action 'INVALID_VALUE'">INVALID_VALUE</error>")
  }

  fun testExecutorActions() {
    ActionUtil.getAction("Run")
    ActionUtil.getAction("RunClass")

    ActionUtil.getActionGroup("<error descr="Cannot resolve group 'Run'">Run</error>")
  }
}