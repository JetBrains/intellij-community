// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.impl.EditorCompositePanel
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.JComponent

internal class EditorSearchComponentState private constructor(private val component: JComponent) : Disposable {
  private val trackers = HashSet<SearchReplaceComponentTracker>()

  val hasSearchComponents: Boolean
    @RequiresEdt get() = trackers.isNotEmpty()

  @RequiresEdt
  fun add(tracker: SearchReplaceComponentTracker) {
    trackers.add(tracker)
  }

  @RequiresEdt
  fun remove(tracker: SearchReplaceComponentTracker) {
    trackers.remove(tracker)
  }

  @RequiresEdt
  override fun dispose() {
    for (tracker in trackers.toList()) {
      tracker.editorDisposed(this)
    }
    trackers.clear()
    if (get(component) === this) component.putUserData(KEY, null)
  }

  companion object {
    private val KEY = Key.create<EditorSearchComponentState>("Islands.EditorSearchComponentState")

    @RequiresEdt
    fun get(component: JComponent): EditorSearchComponentState? = component.getUserData(KEY)

    @RequiresEdt
    fun getOrCreate(panel: EditorCompositePanel): EditorSearchComponentState? {
      get(panel)?.let { return it }
      val state = EditorSearchComponentState(panel)
      if (!Disposer.tryRegister(panel.composite, state)) return null
      panel.putUserData(KEY, state)
      return state
    }
  }
}
