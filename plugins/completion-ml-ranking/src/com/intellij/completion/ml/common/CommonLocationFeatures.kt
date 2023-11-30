// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.common

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.completion.ml.ngram.NGram
import com.intellij.completion.ml.util.prefix
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiTreeUtil
import kotlin.math.abs

class CommonLocationFeatures : ContextFeatureProvider {
  override fun getName(): String = "common"
  override fun calculateFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
    val result = mutableMapOf<String, MLFeatureValue>()

    putNGramScorers(environment)

    result.addTextFeatures(environment)

    val project = environment.lookup.project
    if (DumbService.isDumb(project)) {
      result["dumb_mode"] = MLFeatureValue.binary(true)
    }

    result.addProjectFeatures(project)

    val caseSensitive = CaseSensitivity.fromSettings(CodeInsightSettings.getInstance())
    if (caseSensitive != CaseSensitivity.NONE) {
      result["case_sensitivity"] = MLFeatureValue.categorical(caseSensitive)
    }

    val position = environment.parameters.position
    result["is_after_dot"] = MLFeatureValue.binary(isAfterDot(position))

    result.addPsiParents(position, 10)

    return result
  }

  private fun MutableMap<String, MLFeatureValue>.addTextFeatures(environment: CompletionEnvironment) {
    val lookup = environment.lookup
    val editor = lookup.topLevelEditor
    val caretOffset = lookup.lookupStart
    val logicalPosition = editor.offsetToLogicalPosition(caretOffset)
    val document = editor.document
    val lineStartOffset = document.getLineStartOffset(logicalPosition.line)
    val lineEndOffset = document.getLineEndOffset(logicalPosition.line)
    val position = environment.parameters.position
    val prefixLength = lookup.prefix().length
    val linePrefix = document.getText(TextRange(lineStartOffset, caretOffset))
    val lineSuffix = document.getText(TextRange(caretOffset, lineEndOffset))
    val textLength = document.textLength

    val prefixToDrop = if (prefixLength > 0) prefixLength else 0
    putContextSimilarityScorers(linePrefix.dropLast(prefixToDrop), position, environment)

    this["line_num"] = MLFeatureValue.float(logicalPosition.line)
    this["col_num"] = MLFeatureValue.float(logicalPosition.column)
    this["indent_level"] = MLFeatureValue.float(LocationFeaturesUtil.indentLevel(linePrefix, EditorUtil.getTabSize(editor)))
    this["is_in_line_beginning"] = MLFeatureValue.binary(linePrefix.isBlank())
    this["is_in_line_end"] = MLFeatureValue.binary(lineSuffix.isBlank())
    this["prefix_length"] = MLFeatureValue.float(prefixLength)
    captureNearestNonEmptyLineInfo(document, logicalPosition, true)
    captureNearestNonEmptyLineInfo(document, logicalPosition, false)
    this["offset"] = MLFeatureValue.float(caretOffset)
    this["text_length"] = MLFeatureValue.float(textLength)
    this["offset_text_length_ratio"] = MLFeatureValue.float(if (textLength == 0) 0.0 else caretOffset.toDouble() / textLength)
    if (linePrefix.isNotBlank()) {
      this["is_whitespace_before_caret"] = MLFeatureValue.binary(linePrefix.last().isWhitespace())
      val trimmedPrefix = linePrefix.trim()
      this["symbols_in_line_before_caret"] = MLFeatureValue.float(trimmedPrefix.length)
      CharCategory.find(trimmedPrefix.last())?.let { this["non_space_symbol_before_caret"] = MLFeatureValue.categorical(it) }
    }
    if (lineSuffix.isNotBlank()) {
      this["is_whitespace_after_caret"] = MLFeatureValue.binary(lineSuffix.first().isWhitespace())
      val trimmedSuffix = lineSuffix.trim()
      this["symbols_in_line_after_caret"] = MLFeatureValue.float(trimmedSuffix.length)
      CharCategory.find(trimmedSuffix.first())?.let { this["non_space_symbol_after_caret"] = MLFeatureValue.categorical(it) }
    }
  }

  private fun MutableMap<String, MLFeatureValue>.captureNearestNonEmptyLineInfo(document: Document,
                                                                                position: LogicalPosition,
                                                                                previous: Boolean) {
    val (name, delta) = if (previous) "previous" to -1 else "following" to 1
    val startLineNumber = position.line
    var lineNumber = startLineNumber + delta
    var text: String? = null
    while (lineNumber >= 0 && lineNumber < document.lineCount && document.getLineText(lineNumber).also { text = it }.isBlank()) lineNumber += delta
    this["${name}_empty_lines_count"] = MLFeatureValue.float(abs(lineNumber - startLineNumber) - 1)
    text?.let { this["${name}_non_empty_line_length"] = MLFeatureValue.float(it.length) }
  }

  private fun Document.getLineText(line: Int) = getText(TextRange(getLineStartOffset(line), getLineEndOffset(line)))

  private fun MutableMap<String, MLFeatureValue>.addProjectFeatures(project: Project) {
    val projectInfo = CurrentProjectInfo.getInstance(project)
    if (projectInfo.isIdeaProject) {
      this["is_idea_project"] = MLFeatureValue.binary(true)
    }
    this["modules_count"] = MLFeatureValue.numerical(projectInfo.modulesCount.roundDown())
    this["libraries_count"] = MLFeatureValue.numerical(projectInfo.librariesCount.roundDown())
    this["files_count"] = MLFeatureValue.numerical(projectInfo.filesCount.roundDown())
  }

  private fun putNGramScorers(environment: CompletionEnvironment) {
    for ((key, scorer) in NGram.getScorers(environment.parameters, 4))
      environment.putUserData(key, scorer)
  }

  private fun putContextSimilarityScorers(line: String, position: PsiElement, environment: CompletionEnvironment) {
    environment.putUserData(ContextSimilarityUtil.LINE_SIMILARITY_SCORER_KEY,
                            ContextSimilarityUtil.createLineSimilarityScorer(line))
    environment.putUserData(ContextSimilarityUtil.PARENT_SIMILARITY_SCORER_KEY,
                            ContextSimilarityUtil.createParentSimilarityScorer(position))
  }

  private fun Int.roundDown(base: Int = 10): Int = generateSequence(1) { it * base }.first { it * base > this }

  private fun MutableMap<String, MLFeatureValue>.addPsiParents(position: PsiElement, numParents: Int) {
    // First parent is always referenceExpression
    var curParent: PsiElement? = position.parent ?: return
    for (i in 1..numParents) {
      curParent = curParent?.parent ?: return
      val parentName = "parent_$i"
      this[parentName] = MLFeatureValue.className(curParent::class.java)
      if (curParent is PsiFileSystemItem) return
    }
  }

  private fun isAfterDot(position: PsiElement): Boolean {
    val prev = PsiTreeUtil.prevVisibleLeaf(position)
    return prev != null && prev.text == "."
  }

  private enum class CaseSensitivity {
    NONE, ALL, FIRST_LETTER;

    companion object {
      fun fromSettings(settings: CodeInsightSettings): CaseSensitivity {
        return when (settings.completionCaseSensitive) {
          CodeInsightSettings.ALL -> ALL
          CodeInsightSettings.FIRST_LETTER -> FIRST_LETTER
          else -> NONE
        }
      }
    }
  }
}
