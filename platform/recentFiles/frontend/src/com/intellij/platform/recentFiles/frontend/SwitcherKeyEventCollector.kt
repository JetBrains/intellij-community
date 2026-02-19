// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.AWTEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

internal class SwitcherKeyEventCollector(initialActionEvent: AnActionEvent, parentDisposable: Disposable) : IdeEventQueue.NonLockedEventDispatcher {
  val initialEvent: InputEvent? = initialActionEvent.inputEvent

  private val keyReleaseEvents = ArrayList<KeyEvent>()

  init {
    IdeEventQueue.getInstance().addDispatcher(this, parentDisposable)
  }

  fun getKeyReleaseEventsSoFar(): List<KeyEvent> = keyReleaseEvents.toList()

  override fun dispatch(e: AWTEvent): Boolean {
    if (e.id == KeyEvent.KEY_RELEASED && (e as KeyEvent).keyCode in MODIFIERS) {
      keyReleaseEvents += e
    }
    return false
  }
}

private val MODIFIERS = setOf(
  KeyEvent.VK_ALT,
  KeyEvent.VK_ALT_GRAPH,
  KeyEvent.VK_CONTROL,
  KeyEvent.VK_META,
)
