// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Service
@State(name = "EditorDiffPreview.Settings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class EditorDiffPreviewFilesManager(private val project: Project) :
  SimplePersistentStateComponent<EditorDiffPreviewFilesManager.State>(State()),
  Disposable {

  class State : BaseState() {
    var openInNewWindow by property(false)
  }

  init {
    project.messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file is PreviewDiffVirtualFile && source is FileEditorManagerEx) {
          shouldOpenInNewWindow = source.findFloatingWindowForFile(file) != null
        }
      }
    })
  }

  var shouldOpenInNewWindow: Boolean
    get() = state.openInNewWindow
    set(value) {
      state.openInNewWindow = value
    }

  fun openFile(file: PreviewDiffVirtualFile, focusEditor: Boolean): Array<out FileEditor> {
    with(FileEditorManager.getInstance(project) as FileEditorManagerImpl) {
      if (!shouldOpenInNewWindow) {
        return openFile(file, focusEditor, true)
      }

      if (isFileOpen(file)) {
        if (focusEditor) {
          findFloatingWindowForFile(file)?.run {
            requestFocus(true)
            return findFileComposite(file)?.also { setSelectedEditor(it, focusEditor) }?.editors.orEmpty()
          }
        }

        return getEditors(file)
      }

      return openFileInNewWindow(file).first
    }
  }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(project: Project): EditorDiffPreviewFilesManager = project.service()

    @JvmStatic
    fun FileEditorManagerEx.findFloatingWindowForFile(file: VirtualFile): EditorWindow? {
      return windows.find { it.owner.isFloating && it.isFileOpen(file) }
    }
  }
}
