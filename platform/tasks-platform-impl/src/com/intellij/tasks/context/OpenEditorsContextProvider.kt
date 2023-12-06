// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.context

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.DockableEditorTabbedContainer
import com.intellij.openapi.fileEditor.impl.EditorSplitterState
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.progress.runBlockingModalWithRawProgressReporter
import com.intellij.openapi.project.Project
import com.intellij.tasks.TaskBundle
import com.intellij.ui.docking.DockManager
import com.intellij.ui.docking.impl.DockManagerImpl
import org.jdom.Element
import java.awt.KeyboardFocusManager
import java.awt.Window

private class OpenEditorsContextProvider : WorkingContextProvider() {
  override fun getId(): String = "editors"

  override fun getDescription(): String = TaskBundle.message("open.editors.and.positions")

  override fun saveContext(project: Project, element: Element) {
    val fileEditorManager = getFileEditorManager(project)
    fileEditorManager?.mainSplitters?.writeExternal(element)
    element.addContent((DockManager.getInstance(project) as DockManagerImpl).state)
  }

  override fun loadContext(project: Project, element: Element) {
    val requestFocus = editorHadFocus()

    val fileEditorManager = getFileEditorManager(project)
    if (fileEditorManager != null) {
      runBlockingModalWithRawProgressReporter(project, TaskBundle.message("open.editors.and.positions")) {
        fileEditorManager.mainSplitters.restoreEditors(state = EditorSplitterState(element), requestFocus = requestFocus)
      }
    }
    val dockState = element.getChild("state")
    if (dockState != null) {
      val dockManager = DockManager.getInstance(project) as DockManagerImpl
      dockManager.loadState(dockState)
      dockManager.readState(requestFocus = requestFocus)
    }
  }

  private fun editorHadFocus(): Boolean {
    var c = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    while (c != null) {
      if (c is Window) return false
      if (c is EditorsSplitters) return true
      c = c.parent
    }
    return true // if editor was focused, but was closed by `clearContext` call, we'll get here
  }

  override fun clearContext(project: Project) {
    val fileEditorManager = getFileEditorManager(project)
    if (fileEditorManager != null) {
      fileEditorManager.closeAllFiles()
      fileEditorManager.mainSplitters.clear()
    }
    val dockManager = DockManager.getInstance(project) as DockManagerImpl
    for (container in dockManager.containers) {
      (container as? DockableEditorTabbedContainer)?.closeAll()
    }
  }
}

private fun getFileEditorManager(project: Project): FileEditorManagerImpl? {
  return FileEditorManager.getInstance(project) as? FileEditorManagerImpl
}