// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hints

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.actions.ShortNameType
import com.intellij.openapi.vcs.annotate.AnnotationsPreloader
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect
import com.intellij.openapi.vcs.annotate.LineAnnotationAspectAdapter
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.util.text.nullize
import com.intellij.vcs.CacheableAnnotationProvider
import java.awt.event.MouseEvent
import java.lang.Integer.min
import javax.swing.JComponent

class VcsCodeVisionProvider : CodeVisionProvider<Unit> {
  companion object {
    const val id: String = "vcs.code.vision"
  }

  override fun precomputeOnUiThread(editor: Editor) {

  }


  override fun computeForEditor2(editor: Editor, uiData: Unit): CodeVisionState {
    return runReadAction {
      val project = editor.project ?: return@runReadAction CodeVisionState.NotReady()
      val document = editor.document
      val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@runReadAction CodeVisionState.NotReady()
      val language = file.language

      val aspect = getAspect(file, editor) ?: return@runReadAction CodeVisionState.Ready(emptyList())

      val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()

      try {
        val visionLanguageContext = VcsCodeVisionLanguageContext.providersExtensionPoint.forLanguage(language)
                                    ?: return@runReadAction CodeVisionState.NotReady()
        val traverser = SyntaxTraverser.psiTraverser(file)
        for (element in traverser.preOrderDfsTraversal()) {
          if (visionLanguageContext.isAccepted(element)) {
            val textRange = InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
            val length = editor.document.textLength
            val adjustedRange = TextRange(min(textRange.startOffset, length), min(textRange.endOffset, length))
            val codeAuthorInfo = getCodeAuthorInfo(element.project, adjustedRange, editor, aspect)
            val text = codeAuthorInfo.getText()
            val icon = if (codeAuthorInfo.mainAuthor != null) AllIcons.Vcs.Author else null
            val clickHandler = CodeAuthorClickHandler(element, language)
            val entry = ClickableTextCodeVisionEntry(text, id, onClick = clickHandler, icon, text, text, emptyList())
            entry.showInMorePopup = false
            lenses.add(adjustedRange to entry)
          }
        }
      }
      catch (e: Exception) {
        e.printStackTrace()
        throw e
      }
      return@runReadAction CodeVisionState.Ready(lenses)
    }
  }

  override fun getPlaceholderCollector(editor: Editor, psiFile: PsiFile?): CodeVisionPlaceholderCollector? {
    if (psiFile == null) return null
    val language = psiFile.language
    val visionLanguageContext = VcsCodeVisionLanguageContext.providersExtensionPoint.forLanguage(language) ?: return null
    return object: BypassBasedPlaceholderCollector {
      override fun collectPlaceholders(element: PsiElement, editor: Editor): List<TextRange> {
        val ranges = ArrayList<TextRange>()
        if (visionLanguageContext.isAccepted(element)) {
          ranges.add(InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element))
        }
        return ranges
      }
    }
  }

  override val name: String
    get() = VcsBundle.message("label.code.author.inlay.hints")
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = emptyList()
  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Default
  override val id: String
    get() = Companion.id
}

// implemented not as inplace lambda to avoid capturing project/editor
private class CodeAuthorClickHandler(element: PsiElement, private val language: Language) : (MouseEvent?, Editor) -> Unit {
  private val elementPointer = SmartPointerManager.createPointer(element)

  override fun invoke(event: MouseEvent?, editor: Editor) {
    event ?: return
    val component = event.component as? JComponent ?: return
    invokeAnnotateAction(event, component)
    val element = elementPointer.element ?: return
    val visionLanguageContext = VcsCodeVisionLanguageContext.providersExtensionPoint.forLanguage(language)
    visionLanguageContext.handleClick(event, editor, element)
  }
}

private fun invokeAnnotateAction(event: MouseEvent, contextComponent: JComponent) {
  val action = ActionManager.getInstance().getAction("Annotate")
  ActionUtil.invokeAction(action, contextComponent, ActionPlaces.EDITOR_INLAY, event, null)
}

private fun getCodeAuthorInfo(project: Project, range: TextRange, editor: Editor, authorAspect: LineAnnotationAspect): VcsCodeAuthorInfo {
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

fun getAspect(file: PsiFile, editor: Editor): LineAnnotationAspect? {
  if (hasPreviewInfo(file)) return LineAnnotationAspectAdapter.NULL_ASPECT
  val virtualFile = file.virtualFile ?: return null
  val annotation = getAnnotation(file.project, virtualFile, editor) ?: return null
  return annotation.aspects.find { it.id == LineAnnotationAspect.AUTHOR }
}

private val VCS_CODE_AUTHOR_ANNOTATION = Key.create<FileAnnotation>("Vcs.CodeAuthor.Annotation")

private fun getAnnotation(project: Project, file: VirtualFile, editor: Editor): FileAnnotation? {
  editor.getUserData(VCS_CODE_AUTHOR_ANNOTATION)?.let { return it }

  val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file) ?: return null
  val provider = vcs.annotationProvider as? CacheableAnnotationProvider ?: return null
  val annotation = provider.getFromCache(file) ?: return null

  val annotationDisposable = Disposable {
    unregisterAnnotation(file, annotation)
    annotation.dispose()
  }
  annotation.setCloser {
    editor.putUserData(VCS_CODE_AUTHOR_ANNOTATION, null)
    Disposer.dispose(annotationDisposable)

    project.service<AnnotationsPreloader>().schedulePreloading(file)
  }
  annotation.setReloader { annotation.close() }

  editor.putUserData(VCS_CODE_AUTHOR_ANNOTATION, annotation)
  registerAnnotation(file, annotation)
  ApplicationManager.getApplication().invokeLater {
    EditorUtil.disposeWithEditor(editor, annotationDisposable)
  }

  return annotation
}

private fun registerAnnotation(file: VirtualFile, annotation: FileAnnotation) =
  ProjectLevelVcsManager.getInstance(annotation.project).annotationLocalChangesListener.registerAnnotation(file, annotation)

private fun unregisterAnnotation(file: VirtualFile, annotation: FileAnnotation) =
  ProjectLevelVcsManager.getInstance(annotation.project).annotationLocalChangesListener.unregisterAnnotation(file, annotation)

private val VcsCodeAuthorInfo.isMultiAuthor: Boolean get() = otherAuthorsCount > 0

private fun VcsCodeAuthorInfo.getText(): String {
  val mainAuthorText = ShortNameType.shorten(mainAuthor, ShortNameType.NONE)

  return when {
    mainAuthorText == null -> VcsBundle.message("label.new.code")
    isMultiAuthor && isModified -> VcsBundle.message("label.multi.author.modified.code", mainAuthorText, otherAuthorsCount)
    isMultiAuthor && !isModified -> VcsBundle.message("label.multi.author.not.modified.code", mainAuthorText, otherAuthorsCount)
    !isMultiAuthor && isModified -> VcsBundle.message("label.single.author.modified.code", mainAuthorText)
    else -> mainAuthorText
  }
}