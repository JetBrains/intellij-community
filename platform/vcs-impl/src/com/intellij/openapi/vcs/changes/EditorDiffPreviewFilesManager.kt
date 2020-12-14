// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic

@Service(Service.Level.APP)
@State(name = "EditorDiffPreview.Settings", storages = [(Storage(value = DiffUtil.DIFF_CONFIG))])
class EditorDiffPreviewFilesManager :
  SimplePersistentStateComponent<EditorDiffPreviewFilesManager.State>(State()),
  Disposable {

  class State : BaseState() {
    var openInNewWindow by property(false)
  }

  init {
    val messageBus = ApplicationManager.getApplication().messageBus
    messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file is PreviewDiffVirtualFile && source is FileEditorManagerEx) {
          val isOpenInNewWindow = source.findFloatingWindowForFile(file) != null
          shouldOpenInNewWindow = isOpenInNewWindow
          messageBus.syncPublisher(EditorDiffPreviewFilesListener.TOPIC).shouldOpenInNewWindowChanged(isOpenInNewWindow)
        }
      }
    })
  }

  var shouldOpenInNewWindow: Boolean
    get() = state.openInNewWindow
    set(value) {
      state.openInNewWindow = value
    }

  fun openFile(project: Project,
               file: PreviewDiffVirtualFile,
               focusEditor: Boolean,
               openInNewWindow: Boolean,
               shouldCloseFile: Boolean): Array<out FileEditor> {
    val editorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
    if (shouldCloseFile && editorManager.isFileOpen(file)) {
      editorManager.closeFile(file)
    }
    shouldOpenInNewWindow = openInNewWindow

    return openFile(project, file, focusEditor)
  }

  fun openFile(project: Project, file: PreviewDiffVirtualFile, focusEditor: Boolean): Array<out FileEditor> {
    val editorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
    if (editorManager.isFileOpen(file)) {
      if (focusEditor) {
        focusEditor(project, file)
      }
      return emptyArray()
    }

    if (shouldOpenInNewWindow) {
      return editorManager.openFileInNewWindow(file).first
    }
    else {
      return editorManager.openFile(file, focusEditor, true)
    }
  }

  private fun focusEditor(project: Project, file: PreviewDiffVirtualFile) {
    val editorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
    val window = editorManager.windows.find { it.isFileOpen(file) } ?: return
    val composite = window.findFileComposite(file) ?: return
    window.setSelectedEditor(composite, true)
    window.requestFocus(true)
  }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(): EditorDiffPreviewFilesManager = service()

    @JvmStatic
    fun FileEditorManagerEx.findFloatingWindowForFile(file: VirtualFile): EditorWindow? {
      return windows.find { it.owner.isFloating && it.isFileOpen(file) }
    }
  }
}

interface EditorDiffPreviewFilesListener {
  @RequiresEdt
  fun shouldOpenInNewWindowChanged(shouldOpenInNewWindow: Boolean)

  companion object {
    @JvmField
    val TOPIC: Topic<EditorDiffPreviewFilesListener> =
      Topic(EditorDiffPreviewFilesListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
}
