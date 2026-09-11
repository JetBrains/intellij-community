// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.openapi.fileEditor.impl.EditorCompositePanel
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Component
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import javax.swing.JComponent

internal class SearchReplaceComponentTracker(private val component: JComponent) : HierarchyListener {
  private var editorStates = emptySet<EditorSearchComponentState>()

  init {
    component.addHierarchyListener(this)
    updateEditorStates()
  }

  @RequiresEdt
  override fun hierarchyChanged(event: HierarchyEvent) {
    if (event.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() != 0L) {
      updateEditorStates()
    }
  }

  @RequiresEdt
  private fun updateEditorStates() {
    val states = HashSet<EditorSearchComponentState>()
    var ancestor: Component? = component
    while (ancestor is JComponent) {
      if (ancestor is EditorCompositePanel) {
        EditorSearchComponentState.getOrCreate(ancestor)?.let(states::add)
      }
      ancestor = ancestor.parent
    }
    for (state in editorStates - states) {
      state.remove(this)
    }
    for (state in states - editorStates) {
      state.add(this)
    }
    editorStates = states
  }

  /**
   * Drops the state of a disposed editor.
   * The listener stays for the life of the component, because the component can move to another editor.
   */
  @RequiresEdt
  fun editorDisposed(state: EditorSearchComponentState) {
    editorStates = editorStates - state
  }
}
