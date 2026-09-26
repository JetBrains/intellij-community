// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.ide.actions.OpenInRightSplitAction.Companion.openInRightSplit
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
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
  defaultMode: FileEditorManagerImpl.OpenMode,
  project: Project,
) {
  IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(
    {
      val manager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
      val plan = values.mapNotNull { value ->
        val file = value.virtualFile?.takeIf { it.isValid } ?: return@mapNotNull null
        val mode = RecentFilesNavigator.EP_NAME.computeSafeIfAny { it.getEditorOpenOptions(project, file) } ?: defaultMode
        Triple(value, file, mode)
      }
      val rightSplitFiles = plan.filter { it.third === FileEditorManagerImpl.OpenMode.RIGHT_SPLIT }.map { it.second }
      var splitSubmitted = false
      for ((value, file, mode) in plan) {
        if (mode === FileEditorManagerImpl.OpenMode.RIGHT_SPLIT) {
          if (!splitSubmitted) {
            splitSubmitted = true
            // all of them go into one new split
            openInRightSplit(project = project, files = rightSplitFiles)
          }
        }
        else if (mode == FileEditorManagerImpl.OpenMode.NEW_WINDOW) {
          manager.openFileInNewWindow(file, reuseOpen = true)
        }
        else if (value.editorWindow != null) {
          val editorWindow = findAppropriateWindow(value.editorWindow)
          if (editorWindow != null) {
            manager.openFileImpl2(window = editorWindow, file = file, options = FileEditorOpenOptions(requestFocus = true, waitForCompositeOpen = false))
          }
        }
        else {
          val settings = UISettings.getInstance().state
          val oldValue = settings.reuseNotModifiedTabs
          settings.reuseNotModifiedTabs = false
          manager.openFile(
            file = file,
            window = null,
            options = FileEditorOpenOptions(requestFocus = true, reuseOpen = true, waitForCompositeOpen = false),
          )
          if (LightEdit.owns(project)) {
            LightEditFeatureUsagesUtil.logFileOpen(project, file, OpenPlace.RecentFiles)
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

internal fun closeEditorForFile(selectedFile: SwitcherVirtualFile, project: Project): Boolean {
  val virtualFile = selectedFile.virtualFile ?: return true
  val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl

  val maybePreservedItemWindow = selectedFile.editorWindow
  val window = findAppropriateWindow(maybePreservedItemWindow)
  if (window == null) {
    fileEditorManager.closeFile(virtualFile, false, false)
  }
  else {
    fileEditorManager.closeFile(virtualFile, window)
  }

  return fileEditorManager.getAllEditors(virtualFile).isEmpty()
}