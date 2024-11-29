// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.merge

import com.intellij.diff.DiffEditorTitleCustomizer
import com.intellij.diff.impl.DiffEditorTitleDetails
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer.DiffEditorTitleCustomizerList

internal object GitMergeDialogCustomizerHelper {
  fun getCustomizers(
    project: Project?,
    filePath: FilePath,
    leftCustomizer: DiffEditorTitleCustomizer?,
    rightCustomizer: DiffEditorTitleCustomizer?,
  ): DiffEditorTitleCustomizerList = DiffEditorTitleCustomizerList(
    leftTitleCustomizer = leftCustomizer,
    centerTitleCustomizer = getCentralCustomizer(project, filePath),
    rightTitleCustomizer = rightCustomizer,
  )

  fun getDefaultCustomizers(project: Project?, filePath: FilePath): DiffEditorTitleCustomizerList =
    getCustomizers(project, filePath, null, null)

  private fun getCentralCustomizer(project: Project?, filePath: FilePath): DiffEditorTitleCustomizer =
    DiffEditorTitleDetails.create(project, filePath, DiffBundle.message("merge.version.title.merged.result")).getCustomizer()
}