// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.EditorConflictUtils
import com.intellij.openapi.vcs.EditorConflictUtils.ConflictMarkerType
import com.intellij.openapi.vcs.EditorConflictUtils.ConflictMarkerType.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.DocumentUtil
import com.intellij.util.FileContentUtil
import kotlin.coroutines.experimental.buildSequence

class ConflictsHighlightingPass(val file: PsiFile, document: Document) : TextEditorHighlightingPass(file.project, document) {
  private val highlightInfos: MutableList<HighlightInfo> = mutableListOf()

  override fun doCollectInformation(progress: ProgressIndicator) {
    val conflicts = SyntaxTraverser.psiTraverser(file).filter { it.node?.elementType == TokenType.CONFLICT_MARKER }.toList()
    highlightInfos.addAll(conflicts.map { createMarkerInfo(it) })
    highlightInfos.addAll(conflicts.zipWithNext { begin, end -> createRangeInfo(begin, end) }.filterNotNull())
  }

  private fun createRangeInfo(begin: PsiElement, end: PsiElement): HighlightInfo? {
    val beginType = EditorConflictUtils.getConflictMarkerType(begin.text)
    if (beginType == ConflictMarkerType.AfterLast) return null
    val d = document ?: return null
    val range = getSectionInnerRange(begin, end, d)

    val desiredType = EditorConflictUtils.getActiveMarkerType(myProject)
    val textAttrKey = if (beginType != desiredType) DiffColors.DIFF_DELETED else null

    val infoBuilder = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
    infoBuilder.range(range)
    infoBuilder.needsUpdateOnTyping(false)
    if (textAttrKey != null)
      infoBuilder.textAttributes(textAttrKey)
    return infoBuilder.createUnconditionally()
  }

  private fun createMarkerInfo(element: PsiElement): HighlightInfo {
    val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(element)
      .needsUpdateOnTyping(false)
      .textAttributes(HighlighterColors.BAD_CHARACTER)
      .createUnconditionally()
    getIntentionActions(element).forEach { info.registerFix(it, null, null, null, null) }
    return info
  }

  private fun getIntentionActions(element: PsiElement) = listOf(
    SetActiveIntentionAction(element),
    TakeThisIntentionAction(element)
  )

  override fun doApplyInformationToEditor() {
    if (myDocument == null) return
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, file.textLength, highlightInfos, colorsScheme, id)
  }
}

private fun leafsSeq(e: PsiElement, fwd: Boolean) = buildSequence {
  var cur: PsiElement? = e
  while (true) {
    cur = if (fwd) PsiTreeUtil.nextLeaf(cur!!) else PsiTreeUtil.prevLeaf(cur!!)
    if (cur == null) return@buildSequence
    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    yield(cur!!)
  }
}

private class SetActiveIntentionAction(element: PsiElement) : IntentionAction {
  private val markerText = element.text

  override fun getText() = "Set active"
  override fun getFamilyName() = "Conflict Actions"
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    runWriteAction {
      EditorConflictUtils.setActiveMarkerType(project, EditorConflictUtils.getConflictMarkerType(markerText))
      FileContentUtil.reparseOpenedFiles()
      PsiManager.getInstance(project).dropPsiCaches()
      DaemonCodeAnalyzer.getInstance(project).restart()
    }
  }

  override fun startInWriteAction() = false
}

private fun getSectionInnerRange(begin: PsiElement, end: PsiElement, d: Document) =
  TextRange(begin.rangeWithLine(d).endOffset + 1, end.rangeWithLine(d).startOffset)

private fun getNextMarker(marker: PsiElement): PsiElement? {
  assert(marker.node?.elementType == TokenType.CONFLICT_MARKER)
  val type = EditorConflictUtils.getConflictMarkerType(marker.text) ?: return null
  val next = leafsSeq(marker, true).firstOrNull { it.node?.elementType == TokenType.CONFLICT_MARKER }
  val nextType = EditorConflictUtils.getConflictMarkerType(next?.text) ?: return null
  return when {
    next != null && isNextMarker(type, nextType) -> next
    else -> null
  }
}

private fun getPrevMarker(marker: PsiElement): PsiElement? {
  assert(marker.node?.elementType == TokenType.CONFLICT_MARKER)
  val type = EditorConflictUtils.getConflictMarkerType(marker.text) ?: return null
  val next = leafsSeq(marker, false).firstOrNull { it.node?.elementType == TokenType.CONFLICT_MARKER }
  val nextType = EditorConflictUtils.getConflictMarkerType(next?.text) ?: return null
  return when {
    next != null && isPrevMarker(type, nextType) -> next
    else -> null
  }
}

private fun getFirstMarkerFromGroup(marker: PsiElement): PsiElement {
  var cur = marker
  while (true) {
    val prev = getPrevMarker(cur)
    if (prev == null)
      return cur
    else
      cur = prev
  }
}

private fun getLastMarkerFromGroup(marker: PsiElement): PsiElement {
  var cur = marker
  while (true) {
    val next = getNextMarker(cur)
    if (next == null)
      return cur
    else
      cur = next
  }
}

data class MarkerGroup(val first: PsiElement, val last: PsiElement)

private fun getMarkerGroup(marker: PsiElement) = MarkerGroup(getFirstMarkerFromGroup(marker), getLastMarkerFromGroup(marker))

fun isNextMarker(type: ConflictMarkerType, nextType: ConflictMarkerType?) = when (type) {
  BeforeFirst -> nextType == BeforeMerged || nextType == BeforeLast
  BeforeMerged -> nextType == BeforeLast
  BeforeLast -> nextType == AfterLast
  AfterLast -> false
}

fun isPrevMarker(type: ConflictMarkerType, nextType: ConflictMarkerType?) = when (type) {
  BeforeFirst -> false
  BeforeMerged -> nextType == BeforeFirst
  BeforeLast -> nextType == BeforeMerged || nextType == BeforeFirst
  AfterLast -> nextType == BeforeLast
}

private fun PsiElement.rangeWithLine(d: Document) =
  TextRange(DocumentUtil.getLineStartOffset(textRange.startOffset, d), DocumentUtil.getLineEndOffset(textRange.endOffset, d))

private class TakeThisIntentionAction(element: PsiElement) : IntentionAction {
  private val beginMarkerPtr = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)

  override fun getText() = "Take this"
  override fun getFamilyName() = "Conflict Actions"
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val d = editor?.document ?: return
    val beginMarker = beginMarkerPtr.element ?: return
    val endMarker = getNextMarker(beginMarker) ?: return

    val textRange = endMarker.let { getSectionInnerRange(beginMarker, it, d) }
    val group = getMarkerGroup(beginMarker)
    val outerRange = group.first.rangeWithLine(d).union(group.last.rangeWithLine(d))

    val textToInsert = d.immutableCharSequence.substring(textRange.startOffset, textRange.endOffset)

    runWriteAction {
      d.replaceString(outerRange.startOffset, outerRange.endOffset, textToInsert)
    }
  }

  override fun startInWriteAction() = false
}