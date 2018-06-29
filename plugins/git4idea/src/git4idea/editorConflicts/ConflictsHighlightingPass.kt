// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.lang.EditorConflictSupport.*
import com.intellij.lang.EditorConflictSupport.ConflictMarkerType.*
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.getConflictMarkerType
import com.intellij.openapi.vcs.getSectionInnerRange
import com.intellij.openapi.vcs.rangeWithLine

import com.intellij.psi.*
import git4idea.editorConflicts.intentions.*

class ConflictsHighlightingPass(val file: PsiFile, document: Document) : TextEditorHighlightingPass(file.project, document) {
  private val highlightInfos: MutableList<HighlightInfo> = mutableListOf()

  override fun doCollectInformation(progress: ProgressIndicator) {
    val conflicts = SyntaxTraverser.psiTraverser(file).filter { it.node?.elementType == TokenType.CONFLICT_MARKER }.toList()
    highlightInfos.addAll(conflicts.map { createMarkerInfo(it) })
    highlightInfos.addAll(conflicts.map { createMarkerLineInfo(it) })
    highlightInfos.addAll(conflicts.zipWithNext { begin, end -> createRangeInfo(begin, end) }.filterNotNull())
  }

  private fun createRangeInfo(begin: PsiElement, end: PsiElement): HighlightInfo? {
    val beginType = getConflictMarkerType(begin)
    if (beginType == ConflictMarkerType.AfterLast) return null
    val d = document ?: return null
    val range = getSectionInnerRange(begin, end, d)

    val desiredType = getActiveMarkerType(myProject)
    val textAttrKey = if (beginType != desiredType) DiffColors.DIFF_DELETED else null

    val infoBuilder = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
    infoBuilder.range(range)
    infoBuilder.needsUpdateOnTyping(false)
    if (textAttrKey != null)
      infoBuilder.textAttributes(textAttrKey)
    return infoBuilder.createUnconditionally()
  }

  private fun createMarkerLineInfo(element: PsiElement): HighlightInfo {
    val rangeWithLine = element.rangeWithLine(document!!)
    return HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
      .range(TextRange(rangeWithLine.startOffset, rangeWithLine.endOffset + 1))
      .needsUpdateOnTyping(false)
      .textAttributes(EditorColors.DELETED_TEXT_ATTRIBUTES)
      .createUnconditionally()
  }

  private fun createMarkerInfo(element: PsiElement): HighlightInfo {
    val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(element)
      .needsUpdateOnTyping(false)
      .textAttributes(HighlighterColors.BAD_CHARACTER)
      .createUnconditionally()
    if (getConflictMarkerType(element) != AfterLast) {
      getIntentionActions(element).forEach { info.registerFix(it, null, null, null, null) }
    }
    return info
  }

  private fun getIntentionActions(element: PsiElement) = listOf(
    SetActiveIntentionAction(element),
    TakeIntentionAction(element),
    CompareChangesAction(element),
    RestoreOriginalConflictsAction(element)
  )

  override fun doApplyInformationToEditor() {
    if (myDocument == null) return
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, file.textLength, highlightInfos, colorsScheme, id)
  }
}


