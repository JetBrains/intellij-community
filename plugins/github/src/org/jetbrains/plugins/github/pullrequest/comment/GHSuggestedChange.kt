// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine

class GHSuggestedChange(
  val commentBody: String,
  private val patchHunk: PatchHunk,
  val filePath: String,
  startLine: Int,
  endLine: Int
) {

  val startLineIndex: Int = startLine - 1
  val endLineIndex: Int = endLine - 1

  fun cutContextContent(): List<String> = patchHunk.lines
    .filter { it.type != PatchLine.Type.REMOVE }
    .dropLast(endLineIndex - startLineIndex + 1)
    .takeLast(3)
    .map { it.text }

  fun cutChangedContent(): List<String> = patchHunk.lines
    .filter { it.type != PatchLine.Type.REMOVE }
    .takeLast(endLineIndex - startLineIndex + 1)
    .map { it.text }

  fun cutSuggestedChangeContent(): List<String> {
    return commentBody.lines()
      .dropWhile { !it.startsWith("```suggestion") }
      .drop(1)
      .takeWhile { !it.startsWith("```") }
  }

  companion object {
    fun containsSuggestedChange(markdownText: String): Boolean = markdownText.lines().any { it.startsWith("```suggestion") }
  }
}