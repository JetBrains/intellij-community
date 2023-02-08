// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.ui.codereview.timeline.TimelineDiffComponentFactory
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class GHPRReviewThreadDiffComponentFactory(private val project: Project, private val editorFactory: EditorFactory) {

  fun createComponent(diffHunk: String, startLine: Int?): JComponent {
    try {
      val patchReader = PatchReader(PatchHunkUtil.createPatchFromHunk("_", diffHunk))
      val patchHunk = patchReader.readTextPatches().firstOrNull()?.hunks?.firstOrNull()
                      ?: throw IllegalStateException("Could not parse diff hunk")

      return TimelineDiffComponentFactory.createDiffComponent(project, editorFactory, patchHunk, startLine != null)
    }
    catch (e: Exception) {
      throw IllegalStateException("Could not create diff", e)
    }
  }
}
