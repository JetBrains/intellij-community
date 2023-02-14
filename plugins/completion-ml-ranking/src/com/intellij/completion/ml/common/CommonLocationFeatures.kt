// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.common

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.completion.ml.ngram.NGram
import com.intellij.completion.ml.util.prefix
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiTreeUtil

class CommonLocationFeatures : ContextFeatureProvider {
  override fun getName(): String = "common"
  override fun calculateFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
    val lookup = environment.lookup
    val editor = lookup.topLevelEditor
    val caretOffset = lookup.lookupStart
    val logicalPosition = editor.offsetToLogicalPosition(caretOffset)
    val lineStartOffset = editor.document.getLineStartOffset(logicalPosition.line)
    val position = environment.parameters.position
    val prefixLength = lookup.prefix().length
    val linePrefix = editor.document.getText(TextRange(lineStartOffset, caretOffset))

    putNGramScorers(environment)
    val prefixToDrop = if (prefixLength > 0) prefixLength else 0
    putContextSimilarityScorers(linePrefix.dropLast(prefixToDrop), position, environment)

    val result = mutableMapOf(
      "line_num" to MLFeatureValue.float(logicalPosition.line),
      "col_num" to MLFeatureValue.float(logicalPosition.column),
      "indent_level" to MLFeatureValue.float(LocationFeaturesUtil.indentLevel(linePrefix, EditorUtil.getTabSize(editor))),
      "is_in_line_beginning" to MLFeatureValue.binary(StringUtil.isEmptyOrSpaces(linePrefix))
    )

    result["is_completion_performance_mode"] = MLFeatureValue.binary(false)

    if (DumbService.isDumb(lookup.project)) {
      result["dumb_mode"] = MLFeatureValue.binary(true)
    }

    val projectInfo = CurrentProjectInfo.getInstance(lookup.project)
    if (projectInfo.isIdeaProject) {
      result["is_idea_project"] = MLFeatureValue.binary(true)
    }
    result["modules_count"] = MLFeatureValue.Companion.numerical(projectInfo.modulesCount.roundDown())
    result["libraries_count"] = MLFeatureValue.Companion.numerical(projectInfo.librariesCount.roundDown())
    result["files_count"] = MLFeatureValue.Companion.numerical(projectInfo.filesCount.roundDown())

    val caseSensitive = CaseSensitivity.fromSettings(CodeInsightSettings.getInstance())
    if (caseSensitive != CaseSensitivity.NONE) {
      result["case_sensitivity"] = MLFeatureValue.categorical(caseSensitive)
    }

    result["is_after_dot"] = MLFeatureValue.binary(isAfterDot(position))

    result.addPsiParents(position, 10)
    return result
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

  private fun isAfterDot(position: PsiElement) : Boolean {
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