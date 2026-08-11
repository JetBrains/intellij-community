// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.wm.ToolWindowId
import javax.swing.JComponent

internal class DefaultEditorEmptyTextProvider : EditorEmptyTextProvider {
  override fun appendEmptyText(splitters: JComponent, sink: EditorEmptyTextSink) {
    sink.appendActionWithShortcuts(IdeBundle.message("empty.text.search.everywhere"), IdeActions.ACTION_SEARCH_EVERYWHERE)
    sink.appendToolWindow(IdeBundle.message("empty.text.project.view"), ToolWindowId.PROJECT_VIEW)
    sink.appendActionWithFirstKeyboardShortcut(IdeBundle.message("empty.text.go.to.file"), "GotoFile")
    sink.appendActionWithFirstKeyboardShortcut(IdeBundle.message("empty.text.recent.files"), IdeActions.ACTION_RECENT_FILES)
    sink.appendActionWithFirstKeyboardShortcut(IdeBundle.message("empty.text.navigation.bar"), "ShowNavBar")
    sink.appendLine(IdeBundle.message("empty.text.drop.files.to.open"))
  }
}
