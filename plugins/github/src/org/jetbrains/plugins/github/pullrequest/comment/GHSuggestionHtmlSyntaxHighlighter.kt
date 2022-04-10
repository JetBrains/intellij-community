// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.lang.Language
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter.Companion.colorHtmlChunk
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.VisibleForTesting

class GHSuggestionHtmlSyntaxHighlighter(
  private val project: Project?,
  private val suggestedChangeInfo: GHSuggestedChange,
) : HtmlSyntaxHighlighter {
  override fun color(language: String?, rawContent: String): HtmlChunk {
    val name = PathUtil.getFileName(suggestedChangeInfo.filePath)
    val fileType = (FileTypeRegistry.getInstance().getFileTypeByFileName(name) as? LanguageFileType) ?: PlainTextFileType.INSTANCE
    val fileLanguage = fileType.language

    val changedContent = suggestedChangeInfo.cutChangedContent().joinToString("\n") { it }

    return HtmlBuilder()
      .append(createColoredChunk(project, fileLanguage, trimStartWithMinIndent(changedContent), DiffColors.DIFF_DELETED))
      .append(createColoredChunk(project, fileLanguage, trimStartWithMinIndent(rawContent), DiffColors.DIFF_INSERTED))
      .toFragment()
  }

  private fun createColoredChunk(project: Project?,
                                 language: Language,
                                 rawContent: String,
                                 textAttributesKey: TextAttributesKey): HtmlChunk {
    val colorsScheme = EditorColorsUtil.getGlobalOrDefaultColorScheme()
    val backgroundColor = colorsScheme.getAttributes(textAttributesKey).backgroundColor

    val styles = """
      background-color: ${ColorUtil.toHtmlColor(backgroundColor)}; 
      margin: 0;
      padding: $PADDING $PADDING;
    """.trimIndent()

    return HtmlChunk
      .tag("pre")
      .style(styles)
      .child(colorHtmlChunk(project, language, rawContent))
  }

  companion object {
    private val PADDING
      get() = JBUI.scale(2)

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