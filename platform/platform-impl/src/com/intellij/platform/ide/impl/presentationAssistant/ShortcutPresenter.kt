// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.Font
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

internal class ShortcutPresenter(private val coroutineScope: CoroutineScope) {
  private val movingActions = java.util.Set.of(
    "EditorLeft", "EditorRight", "EditorDown", "EditorUp",
    "EditorLineStart", "EditorLineEnd", "EditorPageUp", "EditorPageDown",
    "EditorPreviousWord", "EditorNextWord",
    "EditorScrollUp", "EditorScrollDown",
    "EditorTextStart", "EditorTextEnd",
    "EditorDownWithSelection", "EditorUpWithSelection",
    "EditorRightWithSelection", "EditorLeftWithSelection",
    "EditorLineStartWithSelection", "EditorLineEndWithSelection",
    "EditorPageDownWithSelection", "EditorPageUpWithSelection")

  private val typingActions = setOf(IdeActions.ACTION_EDITOR_BACKSPACE, IdeActions.ACTION_EDITOR_ENTER,
                                    IdeActions.ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE)
  private val parentGroupIds = setOf("CodeCompletionGroup", "FoldingGroup", "GoToMenu", "IntroduceActionsGroup")
  private var infoPopupGroup: ActionInfoPopupGroup? = null
  private val parentNames by lazy(::loadParentNames)
  private var lastPresentedActionData: ActionData? = null

  init {
    enable(coroutineScope)
  }

  private fun enable(coroutineScope: CoroutineScope) {
    ApplicationManager.getApplication().messageBus.connect(coroutineScope).subscribe(AnActionListener.TOPIC, object : AnActionListener {
      override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        // Show popups a bit later after action is called, to avoid too many UI processes get triggered.
        // Otherwise, popups may be presented with visible blinks.
        coroutineScope.launch(Dispatchers.EDT) {
          val actionId = serviceAsync<ActionManager>().getId(action) ?: return@launch
          if (!movingActions.contains(actionId) && !typingActions.contains(actionId)) {
            val project = event.project
            val text = event.presentation.text
            showActionInfo(ActionData(actionId = actionId, project = project, actionText = text))
          }
        }
      }
    })
  }

  fun refreshPresentedPopupIfNeeded() {
    if (infoPopupGroup?.isShown == true) {
      infoPopupGroup?.close()
      lastPresentedActionData?.let {
        showActionInfo(it)
      }
    }
  }

  private fun loadParentNames(): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    val actionManager = ActionManager.getInstance()
    for (groupId in parentGroupIds) {
      val group = actionManager.getAction(groupId)
      if (group is ActionGroup) {
        fillParentNames(group, group.getTemplatePresentation().text!!, result)
      }
    }
    return result
  }

  private fun fillParentNames(group: ActionGroup, parentName: String, parentNames: MutableMap<String, String>) {
    val actionManager = ActionManager.getInstance()
    for (item in group.getChildren(null)) {
      when (item) {
        is ActionGroup -> {
          if (!item.isPopup) fillParentNames(item, parentName, parentNames)
        }
        else -> {
          val id = actionManager.getId(item)
          if (id != null) {
            parentNames[id] = parentName
          }
        }
      }
    }

  }

  internal class ActionData(@JvmField val actionId: String, @JvmField val project: Project?, @JvmField val actionText: String?)

  fun showActionInfo(actionData: ActionData) {
    if (actionData.actionId == "UiInspector") {
      return
    }

    val fragments = getActionFragments(actionData)

    val realProject = actionData.project ?: ProjectManager.getInstance().openProjects.firstOrNull()
    if (realProject != null && !realProject.isDisposed && realProject.isOpen) {
      lastPresentedActionData = actionData
      if (infoPopupGroup == null || !infoPopupGroup!!.canBeReused(fragments.size)) {
        infoPopupGroup?.close()
        infoPopupGroup = ActionInfoPopupGroup(realProject, fragments, false)
      }
      else {
        infoPopupGroup!!.updateText(realProject, fragments)
      }
    }
    service<PresentationAssistant>().checkIfMacKeymapIsAvailable()
  }

  private fun getActionFragments(actionData: ActionData): List<TextData> {
    val configuration = service<PresentationAssistant>().configuration

    val actionId = actionData.actionId
    val parentGroupName = parentNames[actionId]
    val actionText = (if (parentGroupName != null) "$parentGroupName ${MacKeymapUtil.RIGHT} " else "") +
                     (actionData.actionText ?: "").removeSuffix("...")

    val fragments = ArrayList<TextData>()
    if (actionText.isNotEmpty()) {
      fragments.add(TextData(actionText))
    }

    val mainKeymap = configuration.mainKeymapKind()
    getShortcutTextData(mainKeymap, configuration.mainKeymapLabel, actionId, actionText)?.let {
      fragments.add(it)
    }

    val alternativeKeymap = configuration.alternativeKeymapKind()
    if (alternativeKeymap != null) {
      val mainShortcut = getShortcutsText(mainKeymap.keymap?.getShortcuts(actionId), mainKeymap)
      getShortcutTextData(alternativeKeymap, configuration.alternativeKeymapLabel, actionId, mainShortcut)?.let {
        fragments.add(it)
      }
    }

    return if (fragments.all { it.subtitle == null }) {
      fragments.map { it.copy(showSubtitle = false) }
    }
    else fragments
  }

  private fun getCustomShortcut(actionId: String, kind: KeymapKind): Array<KeyboardShortcut> {
    fun getShortcutForCloneCaret(keyCode: Int): Array<KeyboardShortcut> {
      val modifierCode = if (kind.isMac) KeyEvent.VK_ALT else KeyEvent.VK_CONTROL
      val modifierMask = if (kind.isMac) KeyEvent.ALT_DOWN_MASK else KeyEvent.CTRL_DOWN_MASK

      return arrayOf(
        KeyboardShortcut(
          KeyStroke.getKeyStroke(modifierCode, 0),
          KeyStroke.getKeyStroke(keyCode, modifierMask)
        )
      )
    }

    return when (actionId) {
      IdeActions.ACTION_EDITOR_CLONE_CARET_BELOW -> getShortcutForCloneCaret(KeyEvent.VK_DOWN)
      IdeActions.ACTION_EDITOR_CLONE_CARET_ABOVE -> getShortcutForCloneCaret(KeyEvent.VK_UP)
      else -> emptyArray()
    }
  }

  private fun getShortcutTextData(keymap: KeymapKind, label: String?, actionId: String, shownShortcut: String): TextData? {
    val shortcuts = keymap.keymap?.getShortcuts(actionId)?.let {
      if (it.isNotEmpty()) it else getCustomShortcut(actionId, keymap)
    }
    val shortcutText = getShortcutsText(shortcuts, keymap)
    if (shortcutText.isEmpty() || shortcutText == shownShortcut) return null

    val title: String
    val titleFont: Font?
    val subtitle: String?

    when {
      !keymap.isMac || SystemInfo.isMac || ActionInfoPanel.DEFAULT_FONT.canDisplayUpTo(shortcutText) == -1 -> {
        title = shortcutText
        titleFont = null
      }
      macKeyStrokesFont != null && macKeyStrokesFont!!.canDisplayUpTo(shortcutText) == -1 -> {
        title = shortcutText
        titleFont = macKeyStrokesFont
      }
      else -> {
        val altShortcutAsWin = getShortcutsText(shortcuts, KeymapKind.WIN)
        if (altShortcutAsWin.isNotEmpty() && shownShortcut != altShortcutAsWin) {
          title = altShortcutAsWin
          titleFont = null
        }
        else return null
      }
    }
    val keymapText = label ?: keymap.defaultLabel
    if (keymapText.isNotEmpty()) subtitle = keymapText
    else subtitle = null

    return TextData(title, titleFont, subtitle)
  }

  private fun getShortcutsText(shortcuts: Array<out Shortcut>?, keymapKind: KeymapKind) =
    when {
      shortcuts == null || shortcuts.isEmpty() -> ""
      else -> getShortcutText(shortcuts[0], keymapKind)
    }

  private fun getShortcutText(shortcut: Shortcut, keymapKind: KeymapKind) =
    when (shortcut) {
      is KeyboardShortcut -> arrayOf(shortcut.firstKeyStroke, shortcut.secondKeyStroke).filterNotNull().joinToString(
        separator = ", ") { getKeystrokeText(it, keymapKind) }
      else -> ""
    }

  private fun getKeystrokeText(keystroke: KeyStroke, keymapKind: KeymapKind) =
    if (keymapKind.isMac) {
      if (keystroke.modifiers == 0 && keystroke.keyCode == KeyEvent.VK_ALT) MacKeymapUtil.OPTION
      else MacKeymapUtil.getKeyStrokeText(keystroke)
    }
    else {
      val modifiers = keystroke.modifiers
      val tokens = arrayOf(
        if (modifiers > 0) getWinModifiersText(modifiers) else null,
        getWinKeyText(keystroke.keyCode)
      )
      tokens.filterNotNull().filter { it.isNotEmpty() }.joinToString(separator = "+").trim()
    }

  fun disable() {
    try {
      infoPopupGroup?.let {
        it.close()
        infoPopupGroup = null
      }
    }
    finally {
      coroutineScope.cancel()
    }
  }
}

internal data class TextData(@NlsSafe val title: String,
                             val titleFont: Font? = null,
                             @NlsSafe val subtitle: String? = null,
                             val showSubtitle: Boolean = true)
