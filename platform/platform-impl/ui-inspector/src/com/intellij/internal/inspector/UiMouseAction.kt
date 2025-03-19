// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.KeymapManagerImpl.Companion.isKeymapManagerInitialized
import com.intellij.openapi.keymap.impl.ui.MouseShortcutPanel
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.AWTEvent
import java.awt.Component
import java.awt.event.MouseEvent

@ApiStatus.Internal
abstract class UiMouseAction(val uiActionId: String) : DumbAwareAction() {
  private val myMouseShortcuts: MutableList<MouseShortcut> = ArrayList()

  init {
    isEnabledInModalContext = true

    updateMouseShortcuts()
    KeymapManagerEx.getInstanceEx().addWeakListener(object : KeymapManagerListener {
      override fun activeKeymapChanged(keymap: Keymap?) {
        updateMouseShortcuts()
      }

      override fun shortcutsChanged(keymap: Keymap, actionIds: @NonNls MutableCollection<String>, fromSettings: Boolean) {
        if (actionIds.contains(uiActionId)) {
          updateMouseShortcuts()
        }
      }
    })

    IdeEventQueue.getInstance().addDispatcher(::handleEvent, ApplicationManager.getApplication())
  }

  private fun handleEvent(event: AWTEvent): Boolean {
    if (event is MouseEvent && event.clickCount > 0 && !myMouseShortcuts.isEmpty()) {
      if (event.component is MouseShortcutPanel) return false

      val mouseShortcut = MouseShortcut(event.button, event.modifiersEx, event.clickCount)
      if (myMouseShortcuts.contains(mouseShortcut)) {
        if (event.id == MouseEvent.MOUSE_PRESSED) {
          val component = UIUtil.getDeepestComponentAt(event.component, event.x, event.y)
                          ?: event.component
          handleClick(component, event)
        }
        return true
      }
    }
    return false
  }

  final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  final override fun update(e: AnActionEvent): Unit = Unit

  final override fun actionPerformed(e: AnActionEvent) {
    val event = e.inputEvent
    if (event is MouseEvent) {
      val component = UIUtil.getDeepestComponentAt(event.component, event.x, event.y)
                      ?: event.component
      handleClick(component, event)
    }
    else {
      val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
                      ?: IdeFocusManager.getInstance(e.project).focusOwner
                      ?: return
      handleClick(component, null)
    }
  }

  protected abstract fun handleClick(component: Component, event: MouseEvent?)

  private fun updateMouseShortcuts() {
    if (isKeymapManagerInitialized && ApplicationManager.getApplication().isInternal) {
      val keymap = KeymapManagerEx.getInstanceEx().activeKeymap
      myMouseShortcuts.clear()
      myMouseShortcuts += keymap.getShortcuts(uiActionId)
        .filterIsInstance<MouseShortcut>()
    }
  }
}