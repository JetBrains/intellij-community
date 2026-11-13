// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorOpenMode
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.EditorTabDiffPreview
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowFor
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowType
import java.util.concurrent.CompletableFuture

internal object CommitToolWindowUtil {
  @JvmStatic
  fun isInWindow(twType: ToolWindowType): Boolean {
    return twType == ToolWindowType.WINDOWED || twType == ToolWindowType.FLOATING
  }


  /** Creates the open request of the diff preview, into a window when the commit tool window forces one. */
  @JvmStatic
  fun openDiff(sourceTwId: String, diffPreview: EditorTabDiffPreview, requestFocus: Boolean): CompletableFuture<List<FileEditor>> {
    val project = diffPreview.project
    var request = FileEditorOpenRequest.withFocus(requestFocus)
    if (forceDiffInEditor(project, sourceTwId)) {
      request = request.withOpenMode(FileEditorOpenMode.NEW_WINDOW)
    }
    return DiffEditorTabFilesManager.getInstance(project).requestShowDiffFile(diffPreview.previewFile, request)
  }

  @JvmStatic
  private fun forceDiffInEditor(project: Project, sourceTwId: String): Boolean {
    val tw = getToolWindowFor(project, sourceTwId) ?: return false
    val isCommitTw = tw.id == ToolWindowId.COMMIT
    val isInWindow = isInWindow(tw.type)
    val isForcedDiffInWindow = Registry.`is`("vcs.commit.dialog.force.diff.in.window")

    return isCommitTw && isInWindow && isForcedDiffInWindow
  }
}