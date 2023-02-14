// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.editor.DIFF_OPENED_IN_NEW_WINDOW
import com.intellij.diff.editor.DiffContentVirtualFile
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.openapi.vfs.VirtualFile

abstract class DefaultDiffEditorTabFilesListener : VcsEditorTabFilesListener {
  final override fun shouldOpenInNewWindowChanged(file: VirtualFile, shouldOpenInNewWindow: Boolean) {
    if (file is DiffContentVirtualFile) {
      shouldOpenInNewWindowChanged(file as DiffContentVirtualFile, shouldOpenInNewWindow)
    }
  }

  abstract fun shouldOpenInNewWindowChanged(diffFile: DiffContentVirtualFile, shouldOpenInNewWindow: Boolean)
}


class DiffEditorTabStateListener : DefaultDiffEditorTabFilesListener() {

  override fun shouldOpenInNewWindowChanged(diffFile: DiffContentVirtualFile, shouldOpenInNewWindow: Boolean) {
    DiffEditorTabFilesManager.isDiffInEditor = !shouldOpenInNewWindow
    (diffFile as? VirtualFile)?.putUserData(DIFF_OPENED_IN_NEW_WINDOW, if (shouldOpenInNewWindow) true else null)
  }
}
