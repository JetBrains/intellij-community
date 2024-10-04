// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.APP)
@State(name = "VcsEditorTab.Settings", storages = [(Storage(value = "vcs.xml"))])
class VcsEditorTabFilesManager :
  SimplePersistentStateComponent<VcsEditorTabFilesManager.State>(State()),
  Disposable {

  class State : BaseState() {
    var openInNewWindow by property(false)
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
               shouldCloseFile: Boolean): List<FileEditor> {
    val editorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
    if (shouldCloseFile && editorManager.isFileOpen(file)) {
      editorManager.closeFile(file)
    }
    shouldOpenInNewWindow = openInNewWindow

    return openFile(project, file, focusEditor)
  }

  fun openFile(project: Project, file: VirtualFile, focusEditor: Boolean): List<FileEditor> {
    val editorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl

    if (!ClientId.isCurrentlyUnderLocalId) {
      // do not use FileEditorManagerImpl.getWindows - these are not implemented for clients
      return editorManager.openFile(file = file, focusEditor = focusEditor, searchForOpen = true).toList()
    }

    return editorManager.openFile(
      file = file,
      window = null,
      options = FileEditorOpenOptions(
        openMode = if (shouldOpenInNewWindow) FileEditorManagerImpl.OpenMode.NEW_WINDOW else FileEditorManagerImpl.OpenMode.DEFAULT,
        isSingletonEditorInWindow = true,
        reuseOpen = true,
        requestFocus = focusEditor,
      ),
    ).allEditors
  }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(): VcsEditorTabFilesManager = service()
  }
}

@ApiStatus.Internal
interface VcsEditorTabFilesListener {
  @RequiresEdt
  fun shouldOpenInNewWindowChanged(file: VirtualFile, shouldOpenInNewWindow: Boolean)

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<VcsEditorTabFilesListener> =
      Topic(VcsEditorTabFilesListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
}
