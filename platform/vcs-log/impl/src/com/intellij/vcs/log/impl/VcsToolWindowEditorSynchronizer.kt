// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.diff.editor.VCSContentVirtualFile
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener

@Deprecated("Unused, to be removed")
internal class VcsToolWindowEditorSynchronizer {
  private class MyFileEditorListener : FileEditorManagerListener {
    override fun selectionChanged(e: FileEditorManagerEvent) {
      val file = e.newFile
      if (file is VCSContentVirtualFile) {
        val tabSelector = file.getUserData(VCSContentVirtualFile.TabSelector)
        if (tabSelector != null) {
          tabSelector()
        }
      }
    }
  }
}
