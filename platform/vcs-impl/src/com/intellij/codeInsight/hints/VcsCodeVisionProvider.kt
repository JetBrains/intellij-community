// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hints

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.CodeVisionState.Companion.READY_EMPTY
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.hints.Result.Companion.SUCCESS_EMPTY
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
import com.intellij.openapi.vcs.AbstractVcs
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
import com.intellij.util.application
import com.intellij.util.text.nullize
import com.intellij.vcs.CacheableAnnotationProvider
import java.awt.event.MouseEvent
import java.lang.Integer.min
import javax.swing.JComponent

class VcsCodeVisionProvider : CodeVisionProvider<Unit> {
  companion object {
    const val id: String = "vcs.code.vision"
  }

  override fun isAvailableFor(project: Project): Boolean {
    return VcsCodeVisionLanguageContext.providersExtensionPoint.hasAnyExtensions()
  }

  override fun precomputeOnUiThread(editor: Editor) {

  }

  override fun preparePreview(editor: Editor, file: PsiFile) {
    addPreviewInfo(editor)
  }

  override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
    return runReadAction {
      val project = editor.project ?: return@runReadAction READY_EMPTY
      val document = editor.document
      val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@runReadAction CodeVisionState.NotReady
      val language = file.language

      val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file.virtualFile) ?: return@runReadAction READY_EMPTY
      if ("Git" != vcs.name) {
        return@runReadAction READY_EMPTY
      }

      val aspectResult = getAspect(file, editor)
      if (aspectResult.isSuccess.not()) return@runReadAction CodeVisionState.NotReady
      val aspect = aspectResult.result ?: return@runReadAction READY_EMPTY

      val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()

      try {
        val visionLanguageContext = VcsCodeVisionLanguageContext.providersExtensionPoint.forLanguage(language)
                                    ?: return@runReadAction READY_EMPTY
        val traverser = SyntaxTraverser.psiTraverser(file)
        for (element in traverser.preOrderDfsTraversal()) {
          if (visionLanguageContext.isAccepted(element)) {
            val textRange = InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
            val length = editor.document.textLength
            val adjustedRange = TextRange(min(textRange.startOffset, length), min(textRange.endOffset, length))
            val codeAuthorInfo = PREVIEW_INFO_KEY.get(editor) ?: getCodeAuthorInfo(element.project, adjustedRange, editor, aspect)
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

  override fun collectPlaceholders(editor: Editor): List<TextRange> {

    application.assertReadAccessAllowed()

    val project = editor.project ?: return emptyList()
    val document = editor.document
    val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return emptyList()
    val language = file.language

    val ranges = ArrayList<TextRange>()

    try {
      val visionLanguageContext = VcsCodeVisionLanguageContext.providersExtensionPoint.forLanguage(language) ?: return emptyList()
      val traverser = SyntaxTraverser.psiTraverser(file)
      for (element in traverser.preOrderDfsTraversal()) {
        if (visionLanguageContext.isAccepted(element)) {
          ranges.add(InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element))
        }
      }
    }
    catch (e: Exception) {
      e.printStackTrace()
      throw e
    }
    return ranges
  }

  override fun getPlaceholderCollector(editor: Editor, psiFile: PsiFile?): CodeVisionPlaceholderCollector? {
    if (psiFile == null) return null
    val language = psiFile.language
    val project = editor.project ?: return null
    val visionLanguageContext = VcsCodeVisionLanguageContext.providersExtensionPoint.forLanguage(language) ?: return null
    val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(psiFile.virtualFile) ?: return null
    if ("Git" != vcs.name) {
        return null
    }
    if (vcs.annotationProvider !is CacheableAnnotationProvider) return null
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

private fun getAspect(file: PsiFile, editor: Editor): Result<LineAnnotationAspect?> {
  if (hasPreviewInfo(editor)) return Result.Success(LineAnnotationAspectAdapter.NULL_ASPECT)
  val virtualFile = file.virtualFile ?: return SUCCESS_EMPTY
  val annotationResult = getAnnotation(file.project, virtualFile, editor)
  if (annotationResult.isSuccess.not()) return Result.Failure()

  return Result.Success(annotationResult.result?.aspects?.find { it.id == LineAnnotationAspect.AUTHOR })
}

private val VCS_CODE_AUTHOR_ANNOTATION = Key.create<FileAnnotation>("Vcs.CodeAuthor.Annotation")

private fun getAnnotation(project: Project, file: VirtualFile, editor: Editor): Result<FileAnnotation?> {
  editor.getUserData(VCS_CODE_AUTHOR_ANNOTATION)?.let { return Result.Success(it) }

  val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file) ?: return SUCCESS_EMPTY
  val provider = vcs.annotationProvider as? CacheableAnnotationProvider ?: return SUCCESS_EMPTY
  val isFileInVcs = AbstractVcs.fileInVcsByFileStatus(project, file)
  if (!isFileInVcs) return SUCCESS_EMPTY
  val annotation = provider.getFromCache(file) ?: return Result.Failure()

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

  return Result.Success(annotation)
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

private sealed class Result<out T>(val isSuccess: Boolean, val result: T?){

  companion object{
    val SUCCESS_EMPTY = Success(null)
  }
  class Success<T>(result: T) : Result<T>(true, result)
  class Failure<T> : Result<T>(false, null)
}

private val PREVIEW_INFO_KEY = Key.create<VcsCodeAuthorInfo>("preview.author.info")

private fun addPreviewInfo(editor: Editor) {
  editor.putUserData(PREVIEW_INFO_KEY, VcsCodeAuthorInfo("John Smith", 2, false))
}

private fun hasPreviewInfo(editor: Editor) = PREVIEW_INFO_KEY.get(editor) != null