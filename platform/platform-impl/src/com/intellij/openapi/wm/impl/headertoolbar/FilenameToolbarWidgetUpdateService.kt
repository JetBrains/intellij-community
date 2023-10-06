// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.WindowManagerEx
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class FilenameToolbarWidgetUpdateService(
  private val project: Project,
  coroutineScope: CoroutineScope,
) {

  init {
    project.messageBus.connect(coroutineScope).subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
          update()
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          update()
        }

        override fun selectionChanged(event: FileEditorManagerEvent) {
          update()
        }
      }
    )
  }

  private fun update() {
    WindowManagerEx.getInstanceEx().getFrameHelper(project)?.rootPane?.updateToolbarImmediately()
  }

}
