// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction
import com.intellij.openapi.editor.BlockInlayPriority
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.text.nullize
import java.awt.event.MouseEvent
import javax.swing.JComponent

internal class VcsCodeAuthorInlayHintsCollector(
  editor: Editor,
  private val authorAspect: LineAnnotationAspect,
  private val filter: (PsiElement) -> Boolean
) : FactoryInlayHintsCollector(editor) {

  override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
    if (element is PsiFile) return true
    if (!filter.invoke(element)) return true

    val info = getCodeAuthorInfo(element, editor)
    val presentation = buildPresentation(info, editor).shiftTo(element.startOffset, editor)

    sink.addBlockElement(element.startOffset, false, true, BlockInlayPriority.CODE_VISION, presentation)
    return true
  }

  private fun getCodeAuthorInfo(element: PsiElement, editor: Editor): VcsCodeAuthorInfo {
    val startLine = editor.document.getLineNumber(element.startOffset)
    val endLine = editor.document.getLineNumber(element.endOffset)
    val provider = UpToDateLineNumberProviderImpl(editor.document, element.project)

    val authorsFrequency = (startLine..endLine)
      .map { provider.getLineNumber(it) }
      .mapNotNull { authorAspect.getValue(it).nullize() }
      .groupingBy { it }
      .eachCount()
    val maxFrequency = authorsFrequency.maxOfOrNull { it.value } ?: return VcsCodeAuthorInfo.NEW_CODE

    return VcsCodeAuthorInfo(
      mainAuthor = authorsFrequency.filterValues { it == maxFrequency }.minOf { it.key },
      otherAuthorsCount = authorsFrequency.size - 1,
      isModified = provider.isRangeChanged(startLine, endLine + 1)
    )
  }

  private fun buildPresentation(info: VcsCodeAuthorInfo, editor: Editor): InlayPresentation =
    factory.run {
      val text = smallTextWithoutBackground(info.getText())
      val withIcon = if (info.mainAuthor != null) text.withUserIcon() else text

      referenceOnHover(withIcon) { event, _ -> invokeAnnotateAction(event, editor.component) }
    }

  private fun invokeAnnotateAction(event: MouseEvent, contextComponent: JComponent) {
    val action = ActionManager.getInstance().getAction("Annotate")
    invokeAction(action, contextComponent, "InlayHints.CodeAuthor", event, null)
  }

  private fun InlayPresentation.withUserIcon(): InlayPresentation =
    factory.seq(factory.smallScaledIcon(AllIcons.Vcs.CommitNode), this)

  private fun InlayPresentation.shiftTo(offset: Int, editor: Editor): InlayPresentation {
    val document = editor.document
    val column = offset - document.getLineStartOffset(document.getLineNumber(offset))

    return factory.seq(factory.textSpacePlaceholder(column, true), this)
  }
}

private class VcsCodeAuthorInfo(val mainAuthor: String?, val otherAuthorsCount: Int, val isModified: Boolean) {
  companion object {
    val NEW_CODE: VcsCodeAuthorInfo = VcsCodeAuthorInfo(null, 0, true)
  }
}

private val VcsCodeAuthorInfo.isMultiAuthor: Boolean get() = otherAuthorsCount > 0

private fun VcsCodeAuthorInfo.getText(): String =
  when {
    mainAuthor == null -> message("label.new.code")
    isMultiAuthor && isModified -> message("label.multi.author.modified.code", mainAuthor, otherAuthorsCount)
    isMultiAuthor && !isModified -> message("label.multi.author.not.modified.code", mainAuthor, otherAuthorsCount)
    !isMultiAuthor && isModified -> message("label.single.author.modified.code", mainAuthor)
    else -> mainAuthor
  }