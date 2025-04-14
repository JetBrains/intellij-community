// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.ide.actions.OpenInRightSplitAction.Companion.openInRightSplit
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace
import com.intellij.ide.ui.UISettings
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.toolWindow.ToolWindowEventSource

internal fun openToolWindow(
  window: SwitcherToolWindow,
  isSpeedSearchPopupActive: Boolean,
  project: Project,
) {
  val manager = ToolWindowManager.getInstance(project) as? ToolWindowManagerImpl
  val source = when (isSpeedSearchPopupActive) {
    true -> ToolWindowEventSource.SwitcherSearch
    else -> ToolWindowEventSource.Switcher
  }
  manager?.activateToolWindow(window.id, null, true, source)
  ?: window.window.activate(null, true)
}

internal fun closeToolWindow(
  window: SwitcherToolWindow,
  project: Project,
) {
  val manager = ToolWindowManager.getInstance(project) as? ToolWindowManagerImpl
  manager?.hideToolWindow(id = window.id, moveFocus = false, source = ToolWindowEventSource.CloseFromSwitcher)
  ?: window.window.hide()
}

internal fun openEditorForFile(
  values: List<SwitcherVirtualFile>,
  mode: FileEditorManagerImpl.OpenMode,
  project: Project,
) {
  IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(
    {
      val manager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
      var splitWindow: EditorWindow? = null
      for (value in values) {
        val file = value.virtualFile ?: continue
        if (mode === FileEditorManagerImpl.OpenMode.RIGHT_SPLIT) {
          if (splitWindow == null) {
            splitWindow = openInRightSplit(project = project, file = file, element = null, requestFocus = true)
          }
          else {
            manager.openFile(file, splitWindow, FileEditorOpenOptions().withRequestFocus())
          }
        }
        else if (mode == FileEditorManagerImpl.OpenMode.NEW_WINDOW) {
          manager.openFileInNewWindow(file)
        }
        else {
          val settings = UISettings.getInstance().state
          val oldValue = settings.reuseNotModifiedTabs
          settings.reuseNotModifiedTabs = false
          manager.openFile(
            file = file,
            window = null,
            options = FileEditorOpenOptions(requestFocus = true, reuseOpen = true),
          )
          if (LightEdit.owns(project)) {
            LightEditFeatureUsagesUtil.logFileOpen(project, OpenPlace.RecentFiles)
          }
          if (oldValue) {
            settings.reuseNotModifiedTabs = true
          }
        }
      }
    },
    ModalityState.current(),
  )
}

internal fun closeEditorForFile(selectedFile: SwitcherVirtualFile, project: Project) {
  val virtualFile = selectedFile.virtualFile ?: return
  val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl

  val maybePreservedItemWindow = null
  val window = findAppropriateWindow(maybePreservedItemWindow)
  if (window == null) {
    fileEditorManager.closeFile(virtualFile, false, false)
  }
  else {
    fileEditorManager.closeFile(virtualFile, window)
  }
}