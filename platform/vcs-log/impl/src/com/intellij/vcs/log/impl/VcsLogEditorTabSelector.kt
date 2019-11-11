// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import com.intellij.vcs.log.ui.VcsLogUiImpl

class VcsLogEditorTabSelector(project: Project) : VcsContentEditorTabSelector(project) {

  override fun selectEditorTab(content: Content) {
    val ui = VcsLogContentUtil.getLogUi(content.component)
    if (ui is VcsLogUiImpl) {
      val frame = ui.mainFrame
      frame.openLogEditorTab()
    }
  }

  override fun closeEditorTab(content: Content) {
    val ui = VcsLogContentUtil.getLogUi(content.component)
    if (ui is VcsLogUiImpl) {
      val frame = ui.mainFrame
      frame.closeEditorTab()
    }
  }

  override fun shouldCloseEditorTab(content: Content): Boolean {
    val ui = VcsLogContentUtil.getLogUi(content.component)
    return ui is VcsLogUiImpl
  }
}