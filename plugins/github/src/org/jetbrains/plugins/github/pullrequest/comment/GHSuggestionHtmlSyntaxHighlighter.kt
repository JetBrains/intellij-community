// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.lang.Language
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter.Companion.colorHtmlChunk
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.PathUtil
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.util.GHPatchHunkUtil

class GHSuggestionHtmlSyntaxHighlighter(
  private val project: Project?,
  private val suggestionInfo: GHSuggestionInfo
) : HtmlSyntaxHighlighter {
  override fun color(language: String?, rawContent: String): HtmlChunk {
    val name = PathUtil.getFileName(suggestionInfo.filePath)
    val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(name) as LanguageFileType
    val fileLanguage = fileType.language

    val originalDiffHunk = cutOriginalHunk(suggestionInfo.diffHunk, suggestionInfo.startLine - 1, suggestionInfo.endLine - 1)

    return HtmlBuilder()
      .append(createColoredChunk(project, fileLanguage, trimStartWithMinIndent(originalDiffHunk), DiffColors.DIFF_CONFLICT))
      .append(createColoredChunk(project, fileLanguage, trimStartWithMinIndent(rawContent), DiffColors.DIFF_INSERTED))
      .toFragment()
  }

  private fun createColoredChunk(project: Project?,
                                 language: Language,
                                 rawContent: String,
                                 textAttributesKey: TextAttributesKey): HtmlChunk {
    val colorsScheme = EditorColorsUtil.getGlobalOrDefaultColorScheme()
    val backgroundColor = colorsScheme.getAttributes(textAttributesKey).backgroundColor

    return HtmlChunk
      .tag("pre")
      .style("background-color: ${ColorUtil.toHtmlColor(backgroundColor)}; margin: 0; padding: $PADDING $PADDING;")
      .child(colorHtmlChunk(project, language, rawContent))
  }

  companion object {
    private val PADDING = JBUIScale.scale(2)

    @VisibleForTesting
    fun cutOriginalHunk(diffHunk: String, startLine: Int, endLine: Int): String {
      val patchReader = PatchReader(GHPatchHunkUtil.createPatchFromHunk("", diffHunk))
      patchReader.readTextPatches()
      val patchHunk: PatchHunk = patchReader.textPatches[0].hunks.lastOrNull() ?: return ""

      return patchHunk.lines
        .slice(startLine..endLine)
        .filter { it.type == PatchLine.Type.ADD }
        .joinToString("\n") { it.text }
    }

    @VisibleForTesting
    fun trimStartWithMinIndent(text: String): String {
      val lines = text.lines().let {
        if (it.last() == "") it.dropLast(1)
        else it
      }

      if (lines.isEmpty()) return text

      var minIndent = Integer.MAX_VALUE
      for (line in lines) {
        var currentIndent = 0
        for (symbol in line) {
          if (symbol == ' ') currentIndent++
          else break
        }

        minIndent = Integer.min(minIndent, currentIndent)
      }
      val startIndent = " ".repeat(minIndent)

      return lines.joinToString("\n") { it.removePrefix(startIndent) }
    }
  }
}