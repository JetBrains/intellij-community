// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.editor.DiffContentVirtualFile
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
@State(name = "VcsEditorTab.Settings", storages = [(Storage(value = "vcs.xml"))])
class VcsEditorTabFilesManager :
  SimplePersistentStateComponent<VcsEditorTabFilesManager.State>(State()),
  Disposable {

  class State : BaseState() {
    var openInNewWindow by property(false)
  }

  init {
    val messageBus = ApplicationManager.getApplication().messageBus
    messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        //currently shouldOpenInNewWindow is bound only to diff files
        if (file is DiffContentVirtualFile && source is FileEditorManagerEx) {
          val isOpenInNewWindow = source.findFloatingWindowForFile(file) != null
          shouldOpenInNewWindow = isOpenInNewWindow
          messageBus.syncPublisher(VcsEditorTabFilesListener.TOPIC).shouldOpenInNewWindowChanged(file, isOpenInNewWindow)
        }
      }
    })
  }

  var shouldOpenInNewWindow: Boolean
    get() = state.openInNewWindow
    private set(value) {
      state.openInNewWindow = value
    }

  fun openFile(project: Project,
               file: VirtualFile,
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

  fun openFile(project: Project, file: VirtualFile, focusEditor: Boolean): Array<out FileEditor> {
    val editorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
    if (editorManager.isFileOpen(file)) {
      editorManager.selectAndFocusEditor(file, focusEditor)
      return emptyArray()
    }

    if (shouldOpenInNewWindow) {
      return editorManager.openFileInNewWindow(file).first
    }
    else {
      return editorManager.openFile(file, focusEditor, true)
    }
  }

  private fun FileEditorManagerImpl.selectAndFocusEditor(file: VirtualFile, focusEditor: Boolean) {
    val window = windows.find { it.isFileOpen(file) } ?: return
    val composite = window.getComposite(file) ?: return

    window.setSelectedComposite(composite, focusEditor)
    if (focusEditor) {
      window.requestFocus(true)
      window.toFront()
    }
  }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(): VcsEditorTabFilesManager = service()

    @JvmStatic
    fun FileEditorManagerEx.findFloatingWindowForFile(file: VirtualFile): EditorWindow? {
      return windows.find { it.owner.isFloating && it.isFileOpen(file) }
    }
  }
}

interface VcsEditorTabFilesListener {
  @RequiresEdt
  fun shouldOpenInNewWindowChanged(file: VirtualFile, shouldOpenInNewWindow: Boolean)

  companion object {
    @JvmField
    val TOPIC: Topic<VcsEditorTabFilesListener> =
      Topic(VcsEditorTabFilesListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
}
