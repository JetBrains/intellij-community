// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.tabActions

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings.Companion.instance
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ShadowAction
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tabs.impl.MorePopupAware
import com.intellij.util.BitUtil
import com.intellij.util.ObjectUtils
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent

@Suppress("ComponentNotRegistered")
class CloseTab(c: JComponent,
               val file: VirtualFile,
               val project: Project,
               val editorWindow: EditorWindow,
               parentDisposable: Disposable): AnAction(), DumbAware {

  init {
    ShadowAction(this, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE), c,
                 parentDisposable)
  }

  override fun update(e: AnActionEvent) {
    val pinned = isPinned()
    e.presentation.icon = if (!pinned) AllIcons.Actions.Close else AllIcons.Actions.PinTab
    e.presentation.hoveredIcon = if (!pinned) AllIcons.Actions.CloseHovered else AllIcons.Actions.PinTab
    e.presentation.isVisible = instance.showCloseButton || pinned
    if (pinned && !Registry.get("ide.editor.tabs.interactive.pin.button").asBoolean()) {
      e.presentation.text = ""
      shortcutSet = CustomShortcutSet.EMPTY;
    }
    else {
      if (pinned) {
        shortcutSet = CustomShortcutSet.EMPTY;
        e.presentation.text = TextWithMnemonic.parse(IdeBundle.message("action.unpin.tab")).dropMnemonic(true).text
      }
      else {
        shortcutSet = ObjectUtils.notNull(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_CLOSE), CustomShortcutSet.EMPTY)
        e.presentation.setText(IdeBundle.messagePointer("action.presentation.EditorTabbedContainer.text"))
      }
    }
  }

  private fun isPinned() = editorWindow.isFilePinned(file)

  override fun actionPerformed(e: AnActionEvent) {
    if (isPinned() && e.place == ActionPlaces.EDITOR_TAB) {
      if (Registry.get("ide.editor.tabs.interactive.pin.button").asBoolean()) {
        editorWindow.setFilePinned(file, false)
      }
      return
    }
    val mgr = FileEditorManagerEx.getInstanceEx(project)
    val window: EditorWindow?
    if (ActionPlaces.EDITOR_TAB == e.place) {
      window = editorWindow
    }
    else {
      window = mgr.currentWindow
    }
    if (window != null) {
      if (e.inputEvent is MouseEvent && BitUtil.isSet(e.inputEvent.modifiersEx, InputEvent.ALT_DOWN_MASK)) {
        window.closeAllExcept(file)
      }
      else {
        if (window.getComposite(file) != null) {
          mgr.closeFile(file, window)
        }
      }
    }
    (editorWindow.tabbedPane.tabs as MorePopupAware).let {
      val popup = PopupUtil.getPopupContainerFor(e.inputEvent?.component)
      if (popup != null && it.canShowMorePopup()) {
        it.showMorePopup()
      }
      popup?.cancel()
    }
  }
}