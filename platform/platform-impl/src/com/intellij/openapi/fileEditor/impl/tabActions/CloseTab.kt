// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.tabActions

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings.Companion.instance
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ShadowAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.BitUtil
import java.awt.event.InputEvent
import javax.swing.JComponent

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
      e.presentation.setText("")
    }
    else {
      e.presentation.setText(IdeBundle.messagePointer("action.presentation.EditorTabbedContainer.text"))
    }
  }

  private fun isPinned() = editorWindow.isFilePinned(file)

  override fun actionPerformed(e: AnActionEvent) {
    if (isPinned() && !Registry.get("ide.editor.tabs.interactive.pin.button").asBoolean()) return
    val mgr = FileEditorManagerEx.getInstanceEx(project)
    val window: EditorWindow?
    if (ActionPlaces.EDITOR_TAB == e.place) {
      window = editorWindow
    }
    else {
      window = mgr.currentWindow
    }
    if (window != null) {
      if (BitUtil.isSet(e.modifiers, InputEvent.ALT_MASK)) {
        window.closeAllExcept(file)
      }
      else {
        if (window.findFileComposite(file) != null) {
          mgr.closeFile(file, window)
        }
      }
    }
  }
}