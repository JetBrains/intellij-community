// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector

import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.KeymapManagerImpl.Companion.isKeymapManagerInitialized
import com.intellij.openapi.keymap.impl.ui.MouseShortcutPanel
import com.intellij.openapi.project.DumbAwareAction
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.MouseEvent

abstract class UiMouseAction(val uiActionId: String) : DumbAwareAction() {
  private val myMouseShortcuts: MutableList<MouseShortcut> = ArrayList()

  init {
    isEnabledInModalContext = true

    updateMouseShortcuts()
    KeymapManagerEx.getInstanceEx().addWeakListener(object : KeymapManagerListener {
      override fun activeKeymapChanged(keymap: Keymap?) {
        updateMouseShortcuts()
      }

      override fun shortcutChanged(keymap: Keymap, actionId: String) {
        if (uiActionId == actionId) {
          updateMouseShortcuts()
        }
      }
    })
    Toolkit.getDefaultToolkit().addAWTEventListener(
      { event ->
        if (event is MouseEvent && event.clickCount > 0 && !myMouseShortcuts.isEmpty()) {
          if (event.component is MouseShortcutPanel) return@addAWTEventListener

          val mouseShortcut = MouseShortcut(event.button, event.modifiersEx, event.clickCount)
          if (myMouseShortcuts.contains(mouseShortcut)) {
            event.consume()
          }
        }
      }, AWTEvent.MOUSE_EVENT_MASK)
  }

  private fun updateMouseShortcuts() {
    if (isKeymapManagerInitialized) {
      val keymap = KeymapManagerEx.getInstanceEx().activeKeymap
      myMouseShortcuts.clear()
      myMouseShortcuts += keymap.getShortcuts(uiActionId)
        .filterIsInstance<MouseShortcut>()
    }
  }
}