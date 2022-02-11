// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import java.nio.charset.Charset

class GHSuggestedChangeApplier(
  private val project: Project,
  private val suggestedChange: String,
  private val suggestedChangeInfo: GHSuggestedChangeInfo,
) {
  private val projectDir = project.guessProjectDir()!!

  fun applySuggestedChange(): ApplyPatchStatus {
    val suggestedChangeContent = getSuggestedChangeContent(suggestedChange)
    val suggestedChangePatchHunk = createSuggestedChangePatchHunk(suggestedChangeContent, suggestedChangeInfo)
    val suggestedChangePatch = TextFilePatch(Charset.defaultCharset()).apply {
      beforeName = suggestedChangeInfo.filePath
      afterName = suggestedChangeInfo.filePath
      addHunk(suggestedChangePatchHunk)
    }

    val patchApplier = PatchApplier(project, projectDir, listOf(suggestedChangePatch), null, null)

    return patchApplier.execute(true, false)
  }

  private fun createSuggestedChangePatchHunk(suggestedChangeContent: List<String>, suggestionInfo: GHSuggestedChangeInfo): PatchHunk {
    val suggestedChangePatchHunk = PatchHunk(suggestionInfo.startLine, suggestionInfo.endLine,
                                             suggestionInfo.startLine, suggestionInfo.startLine + suggestedChangeContent.size - 1)

    val changedLines = suggestionInfo.cutChangedContent()
    changedLines.forEach { suggestedChangePatchHunk.addLine(PatchLine(PatchLine.Type.REMOVE, it)) }
    suggestedChangeContent.forEach { suggestedChangePatchHunk.addLine(PatchLine(PatchLine.Type.ADD, it)) }

    return suggestedChangePatchHunk
  }

  private fun getSuggestedChangeContent(comment: String): List<String> {
    return comment.lines()
      .dropWhile { !it.startsWith("```suggestion") }
      .drop(1)
      .takeWhile { !it.startsWith("```") }
  }
}