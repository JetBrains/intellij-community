/*
 * Copyright 2000-2016 Nikolay Chashnikov.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author nik
 */
package org.nik.presentationAssistant

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import java.awt.Font
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class ShortcutPresenter : Disposable {
    private val movingActions = setOf(
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
    private var infoPanel: ActionInfoPanel? = null
    private val parentNames by lazy(::loadParentNames)
    init
    {
        enable()
    }

    private fun enable() {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(AnActionListener.TOPIC, object: AnActionListener {
            override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
                val actionId = ActionManager.getInstance().getId(action) ?: return

                if (!movingActions.contains(actionId) && !typingActions.contains(actionId)) {
                    val project = event.project
                    val text = event.presentation.text
                    showActionInfo(ActionData(actionId, project, text))
                }
            }
        })
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

    class ActionData(val actionId: String, val project: Project?, val actionText: String?)

    private fun MutableList<Pair<String, Font?>>.addText(text: String) {
        this.add(Pair(text, null))
    }

    fun showActionInfo(actionData: ActionData) {
        val actionId = actionData.actionId
        val parentGroupName = parentNames[actionId]
        val actionText = (if (parentGroupName != null) "$parentGroupName ${MacKeymapUtil.RIGHT} " else "") + (actionData.actionText ?: "").removeSuffix("...")

        val fragments = ArrayList<Pair<String, Font?>>()
        if (actionText.isNotEmpty()) {
            fragments.addText("<b>$actionText</b>")
        }

        val mainKeymap = getPresentationAssistant().configuration.mainKeymap
        val shortcutTextFragments = getShortcutTexts(mainKeymap, actionId, actionText)
        if (shortcutTextFragments.isNotEmpty()) {
            if (fragments.isNotEmpty()) fragments.addText(" via&nbsp;")
            fragments.addAll(shortcutTextFragments)
        }

        val alternativeKeymap = getPresentationAssistant().configuration.alternativeKeymap
        if (alternativeKeymap != null) {
            val mainShortcut = getShortcutsText(mainKeymap.getKeymap()?.getShortcuts(actionId), mainKeymap.getKind())
            val altShortcutTextFragments = getShortcutTexts(alternativeKeymap, actionId, mainShortcut)
            if (altShortcutTextFragments.isNotEmpty()) {
                fragments.addText("&nbsp;(")
                fragments.addAll(altShortcutTextFragments)
                fragments.addText(")")
            }
        }

        val realProject = actionData.project ?: ProjectManager.getInstance().openProjects.firstOrNull()
        if (realProject != null && !realProject.isDisposed && realProject.isOpen) {
            if (infoPanel == null || !infoPanel!!.canBeReused()) {
                infoPanel = ActionInfoPanel(realProject, fragments)
            } else {
                infoPanel!!.updateText(realProject, fragments)
            }
        }
        getPresentationAssistant().checkIfMacKeymapIsAvailable()
    }

    private fun getCustomShortcut(actionId: String, kind: KeymapKind): Array<KeyboardShortcut> {
        fun getShortcutForCloneCaret(keyCode: Int): Array<KeyboardShortcut> {
            val modifierCode = when (kind) {
                KeymapKind.MAC -> KeyEvent.VK_ALT
                KeymapKind.WIN -> KeyEvent.VK_CONTROL
            }
            val modifierMask = when (kind) {
                KeymapKind.MAC -> KeyEvent.ALT_DOWN_MASK
                KeymapKind.WIN -> KeyEvent.CTRL_DOWN_MASK
            }
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

    private fun getShortcutTexts(keymap: KeymapDescription, actionId: String, shownShortcut: String): List<Pair<String, Font?>> {
        val fragments = ArrayList<Pair<String, Font?>>()
        val shortcuts = keymap.getKeymap()?.getShortcuts(actionId)?.let {
            if (it.isNotEmpty()) it else getCustomShortcut(actionId, keymap.getKind())
        }
        val shortcutText = getShortcutsText(shortcuts, keymap.getKind())
        if (shortcutText.isEmpty() || shortcutText == shownShortcut) return fragments

        when {
            keymap.getKind() == KeymapKind.WIN || SystemInfo.isMac -> {
                fragments.addText(shortcutText)
            }
            macKeyStrokesFont != null && macKeyStrokesFont!!.canDisplayUpTo(shortcutText) == -1 -> {
                fragments.add(Pair(shortcutText, macKeyStrokesFont))
            }
            else -> {
                val altShortcutAsWin = getShortcutsText(shortcuts, KeymapKind.WIN)
                if (altShortcutAsWin.isNotEmpty() && shownShortcut != altShortcutAsWin) {
                    fragments.addText(altShortcutAsWin)
                }
            }
        }
        val keymapText = keymap.displayText
        if (keymapText.isNotEmpty()) {
            fragments.addText("&nbsp;$keymapText")
        }
        return fragments
    }

    private fun getShortcutsText(shortcuts: Array<out Shortcut>?, keymapKind: KeymapKind) =
        when {
            shortcuts == null || shortcuts.isEmpty() -> ""
            else -> getShortcutText(shortcuts[0], keymapKind)
        }

    private fun getShortcutText(shortcut: Shortcut, keymapKind: KeymapKind) =
        when (shortcut) {
            is KeyboardShortcut -> arrayOf(shortcut.firstKeyStroke, shortcut.secondKeyStroke).filterNotNull().joinToString(separator = ", ") { getKeystrokeText(it, keymapKind) }
            else -> ""
        }

    private fun getKeystrokeText(keystroke: KeyStroke, keymapKind: KeymapKind) =
        when (keymapKind) {
            KeymapKind.MAC -> {
                if (keystroke.modifiers == 0 && keystroke.keyCode == KeyEvent.VK_ALT) MacKeymapUtil.OPTION
                else MacKeymapUtil.getKeyStrokeText(keystroke)
            }
            KeymapKind.WIN -> {
                val modifiers = keystroke.modifiers
                val tokens = arrayOf(
                   if (modifiers > 0) getWinModifiersText(modifiers) else null,
                   getWinKeyText(keystroke.keyCode)
                )
                tokens.filterNotNull().filter { it.isNotEmpty() }.joinToString(separator = "+").trim()
            }
    }

    fun disable() {
        if (infoPanel != null) {
            infoPanel!!.close()
            infoPanel = null
        }
        Disposer.dispose(this)
    }

    override fun dispose() {
    }
}