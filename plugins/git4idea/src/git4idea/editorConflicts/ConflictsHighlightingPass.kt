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
import com.intellij.psi.*
import com.intellij.util.DocumentUtil
import com.intellij.util.FileContentUtil

class ConflictsHighlightingPass(val file: PsiFile, document: Document) : TextEditorHighlightingPass(file.project, document) {
  private val highlightInfos: MutableList<HighlightInfo> = mutableListOf()

  override fun doCollectInformation(progress: ProgressIndicator) {
    val conflicts = SyntaxTraverser.psiTraverser(file).filter { it.node?.elementType == TokenType.CONFLICT_MARKER }.toList()
    highlightInfos.addAll(conflicts.map { createMarkerInfo(it) })
    highlightInfos.addAll(conflicts.zipWithNext { begin, end -> createRangeInfo(begin, end) }.filterNotNull())
  }

  private fun createRangeInfo(begin: PsiElement, end: PsiElement): HighlightInfo? {
    val beginType = EditorConflictUtils.getConflictMarkerType(begin.text)
    if (beginType == EditorConflictUtils.ConflictMarkerType.AfterLast) return null
    val d = document ?: return null

    val desiredType = EditorConflictUtils.getActiveMarkerType(myProject)
    val range = TextRange(DocumentUtil.getLineEndOffset(begin.textOffset, d) + 1, end.textRange.startOffset)
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
    getIntentionActions(element).forEach { info.registerFix(it, null,  null, null, null) }
    return info
  }

  private fun getIntentionActions(element: PsiElement) = listOf(SetActiveIntentionAction(element))

  override fun doApplyInformationToEditor() {
    if (myDocument == null) return
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, file.textLength, highlightInfos, colorsScheme, id)
  }
}

private class SetActiveIntentionAction(element: PsiElement) : IntentionAction {
  private val markerText = element.text

  override fun getText() = "Set Active"
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