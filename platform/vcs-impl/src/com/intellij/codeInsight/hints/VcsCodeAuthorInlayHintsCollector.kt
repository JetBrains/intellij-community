// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.InlayHintsUtils.getDefaultInlayHintsProviderPopupActions
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces.EDITOR_INLAY
import com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction
import com.intellij.openapi.editor.BlockInlayPriority
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsBundle.messagePointer
import com.intellij.openapi.vcs.actions.ShortNameType
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SyntaxTraverser.psiApi
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.text.nullize
import java.awt.event.MouseEvent
import javax.swing.JComponent

internal class VcsCodeAuthorInlayHintsCollector(
  editor: Editor,
  private val authorAspect: LineAnnotationAspect,
  private val filter: (PsiElement) -> Boolean,
  private val getClickHandler: (PsiElement) -> (() -> Unit)
) : FactoryInlayHintsCollector(editor) {

  override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
    if (element is PsiFile) return true
    if (!filter.invoke(element)) return true

    val range = getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
    val info = PREVIEW_INFO_KEY.get(element.containingFile) ?: getCodeAuthorInfo(element.project, range, editor)
    val presentation = buildPresentation(element, info, editor).addContextMenu(element.project)

    sink.addCodeVisionElement(editor, range.startOffset, BlockInlayPriority.CODE_AUTHOR, presentation)
    return true
  }

  private fun getCodeAuthorInfo(project: Project, range: TextRange, editor: Editor): VcsCodeAuthorInfo {
    val startLine = editor.document.getLineNumber(range.startOffset)
    val endLine = editor.document.getLineNumber(range.endOffset)
    val provider = UpToDateLineNumberProviderImpl(editor.document, project)

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

  private fun buildPresentation(element: PsiElement, info: VcsCodeAuthorInfo, editor: Editor): InlayPresentation =
    factory.run {
      val text = smallTextWithoutBackground(info.getText())
      val withIcon = if (info.mainAuthor != null) text.withUserIcon() else text
      val clickHandler = getClickHandler(element)

      referenceOnHover(withIcon) { event, _ ->
        clickHandler()
        invokeAnnotateAction(event, editor.component)
      }
    }

  private fun invokeAnnotateAction(event: MouseEvent, contextComponent: JComponent) {
    val action = ActionManager.getInstance().getAction("Annotate")
    invokeAction(action, contextComponent, EDITOR_INLAY, event, null)
  }

  private fun InlayPresentation.withUserIcon(): InlayPresentation =
    factory.seq(factory.smallScaledIcon(AllIcons.Vcs.Author), this)

  private fun InlayPresentation.addContextMenu(project: Project): InlayPresentation =
    MenuOnClickPresentation(this, project) {
      getDefaultInlayHintsProviderPopupActions(VcsCodeAuthorInlayHintsProvider.KEY, messagePointer("title.code.author.inlay.hints"))
    }

  companion object {
    internal fun getTextRangeWithoutLeadingCommentsAndWhitespaces(element: PsiElement): TextRange {
      val start = psiApi().children(element).firstOrNull { it !is PsiComment && it !is PsiWhiteSpace } ?: element

      return TextRange.create(start.startOffset, element.endOffset)
    }
  }
}

private class VcsCodeAuthorInfo(val mainAuthor: String?, val otherAuthorsCount: Int, val isModified: Boolean) {
  companion object {
    val NEW_CODE: VcsCodeAuthorInfo = VcsCodeAuthorInfo(null, 0, true)
  }
}

private val PREVIEW_INFO_KEY = Key.create<VcsCodeAuthorInfo>("preview.author.info")

fun addPreviewInfo(psiFile: PsiFile) {
  psiFile.putUserData(PREVIEW_INFO_KEY, VcsCodeAuthorInfo("John Smith", 2, false))
}

fun hasPreviewInfo(psiFile: PsiFile) = PREVIEW_INFO_KEY.get(psiFile) != null

private val VcsCodeAuthorInfo.isMultiAuthor: Boolean get() = otherAuthorsCount > 0

private fun VcsCodeAuthorInfo.getText(): String {
  val mainAuthorText = ShortNameType.shorten(mainAuthor, ShortNameType.NONE)

  return when {
    mainAuthorText == null -> message("label.new.code")
    isMultiAuthor && isModified -> message("label.multi.author.modified.code", mainAuthorText, otherAuthorsCount)
    isMultiAuthor && !isModified -> message("label.multi.author.not.modified.code", mainAuthorText, otherAuthorsCount)
    !isMultiAuthor && isModified -> message("label.single.author.modified.code", mainAuthorText)
    else -> mainAuthorText
  }
}