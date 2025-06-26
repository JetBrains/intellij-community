// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.editor.DiffEditorTabFilesUtil.forceOpenInNewWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowFor
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowType

internal object CommitToolWindowUtil {
  @JvmStatic
  fun isInWindow(twType: ToolWindowType): Boolean {
    return twType == ToolWindowType.WINDOWED || twType == ToolWindowType.FLOATING
  }


  @JvmStatic
  fun openDiff(sourceTwId: String, diffPreview: TreeHandlerEditorDiffPreview, requestFocus: Boolean): Boolean {
    if (!diffPreview.hasContent()) return false

    val project = diffPreview.project
    val diffFile = diffPreview.previewFile

    if (forceDiffInEditor(project, sourceTwId)) {
      forceOpenInNewWindow(project, diffFile, requestFocus)
    } else {
      DiffEditorTabFilesManager.getInstance(project).showDiffFile(diffFile, requestFocus)
    }
    return true
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