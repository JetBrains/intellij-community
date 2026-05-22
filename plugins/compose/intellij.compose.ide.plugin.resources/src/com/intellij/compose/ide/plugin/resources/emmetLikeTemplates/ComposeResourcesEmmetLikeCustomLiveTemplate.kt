// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

import com.intellij.codeInsight.template.CustomLiveTemplateBase
import com.intellij.codeInsight.template.CustomTemplateCallback
import com.intellij.compose.ide.plugin.resources.ALL_STRING_TAGS
import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.compose.ide.plugin.resources.isComposeResourcesFile
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle

private val closingTagRegex = Regex(".*</(${ALL_STRING_TAGS.joinToString("|")})>\\s*")
private val valuesDirectoryRegex = Regex("""values(?:-.+)?""")

/**
 * A custom live template that provides Emmet-like abbreviations for Compose Multiplatform resource XML files.
 *
 * It supports generating:
 * - String resources: `[prefix][name]{[value]}[*multiplier]`
 * - String arrays: `[prefix][name][>count]{[value]}[*multiplier]`
 * - Plurals: `[prefix][name][:mode]{[value]}[*multiplier]`
 * - Items: `(item|i){[value]}[*multiplier]` (context-aware for `<plurals>`)
 * - Positional format specifiers: `NUMBER[TYPE]` (e.g. `1` expands to `%1$s`, `2d` to `%2$d`)
 *
 * All shorthand commands are triggered by pressing **Tab**. The tool expands the abbreviation
 * and automatically places the caret inside the first editable field.
 */
internal class ComposeResourcesEmmetLikeCustomLiveTemplate : CustomLiveTemplateBase() {

  override fun computeTemplateKey(callback: CustomTemplateCallback): String? {
    val editor = callback.editor
    val offset = editor.caretModel.offset
    val document = editor.document

    val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
    val lineEnd = document.getLineEndOffset(document.getLineNumber(offset))
    val textBeforeCaret = document.charsSequence.subSequence(lineStart, offset)
    val textAfterCaret = document.charsSequence.subSequence(offset, lineEnd)

    if (textBeforeCaret.isBlank() || textBeforeCaret.isRightAfterClosingXmlTag()) return null

    val lastWord = textBeforeCaret.getCommand() ?: return null

    if (!lastWord.hasValidCommandStart())
      return null

    val isNumeric = lastWord.matches(templateShortcutRegex)

    if (isNumeric && textAfterCaret.isBlank())
      return null
    else if (!isNumeric && (textAfterCaret.isNotBlank() || textBeforeCaret.trimStart().startsWith('<')))
      return null

    return lastWord
  }

  override fun expand(key: String, callback: CustomTemplateCallback) {
    if (tryExpandNumericShortcut(key, callback)) return
    TemplateBuilder.Context.createTemplateFromKey(key, callback)
  }

  override fun isApplicable(callback: CustomTemplateCallback, offset: Int, wrapping: Boolean): Boolean {
    val file = callback.file
    val parentDirectoryName = file.parent?.name ?: return false
    return file.name.endsWith(".xml") &&
           parentDirectoryName.matches(valuesDirectoryRegex) &&
           file.isComposeResourcesFile(setOf(ResourceType.STRING.dirName)) &&
           !wrapping
  }

  override fun supportsWrapping(): Boolean = false
  override fun wrap(selection: String, callback: CustomTemplateCallback): Unit = Unit
  override fun getTitle(): String = ComposeIdeBundle.message("compose.resources.string.action.shortcut.text")
  override fun getShortcut(): Char = '\t'
}

private fun String.hasValidCommandStart(): Boolean =
  when {
    isBlank() -> false
    startsWith('<') -> false
    this == "*" -> false
    endsWith('"') -> false
    endsWith('\'') -> false
    endsWith('*') -> false
    else -> true
  }

private fun CharSequence.isRightAfterClosingXmlTag(): Boolean {
  val trimmed = trimEnd()
  if (trimmed.isEmpty()) return false

  val lastChar = trimmed.last()
  if (lastChar == '>')
    return trimmed.matches(closingTagRegex)

  return false
}

private fun CharSequence.getCommand(): String? {
  if (isEmpty()) return null

  var commandStartIndex = length - 1
  // this handles nested brackets
  var bracketDepth = 0
  while (commandStartIndex >= 0) {
    when (this[commandStartIndex]) {
      '}' -> bracketDepth++
      '{' -> {
        if (bracketDepth <= 0) return null
        bracketDepth--
      }
      ' ' if bracketDepth == 0 -> break
    }
    commandStartIndex--
  }

  if (bracketDepth != 0) return null

  if (commandStartIndex < 0)
    return toString().takeUnless { it.isBlank() }


  return substring(commandStartIndex + 1)
}