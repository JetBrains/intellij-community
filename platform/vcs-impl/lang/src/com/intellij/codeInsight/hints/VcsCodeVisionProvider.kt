// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hints

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.CodeVisionState.Companion.READY_EMPTY
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.actions.ShortNameType
import com.intellij.openapi.vcs.annotate.AnnotationsPreloader
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect.AUTHOR
import com.intellij.openapi.vcs.annotate.LineAnnotationAspectAdapter
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.util.DocumentUtil
import com.intellij.util.text.nullize
import com.intellij.vcs.CacheableAnnotationProvider
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseEvent
import java.lang.Integer.min
import javax.swing.JComponent

@ApiStatus.Internal
class VcsCodeVisionProvider : CodeVisionProvider<Unit> {
  companion object {
    internal const val id: String = "vcs.code.vision"
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
    val project = editor.project ?: return READY_EMPTY
    val document = editor.document
    val fileAnnotationLoading =
      if (hasPreviewInfo(editor)) LoadingFileAnnotation.Loaded(null)
      else when (val annotation = getAnnotation(editor, project, document)) {
        AnnotationResult.NoAnnotation -> return CodeVisionState.Ready(emptyList())
        AnnotationResult.NotReady -> LoadingFileAnnotation.NotReady
        is AnnotationResult.Success -> LoadingFileAnnotation.Loaded(annotation.res)
      }

    return InlayHintsUtils.computeCodeVisionUnderReadAction {
      val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@computeCodeVisionUnderReadAction READY_EMPTY

      val fileLanguage = file.language
      val fileContext = VcsCodeVisionLanguageContext.providersExtensionPoint.forLanguage(fileLanguage)
      val additionalContexts: List<VcsCodeVisionLanguageContext> =
        if (fileContext == null)
          VcsCodeVisionLanguageContext.providersExtensionPoint.point?.extensionList?.map { it.instance }?.filter {
            it.isCustomFileAccepted(file)
          } ?: emptyList()
        else emptyList()
      // If an annotation is not loaded and the context can't be resolved, we can immediately return an empty state.
      if (fileContext == null && additionalContexts.isEmpty()) return@computeCodeVisionUnderReadAction READY_EMPTY
      if (fileAnnotationLoading !is LoadingFileAnnotation.Loaded) return@computeCodeVisionUnderReadAction CodeVisionState.NotReady

      val aspect = when {
        hasPreviewInfo(editor) -> LineAnnotationAspectAdapter.NULL_ASPECT
        fileAnnotationLoading.annotation != null -> {
          val annotation = fileAnnotationLoading.annotation
          handleAnnotationRegistration(annotation, editor, project, VcsUtil.resolveSymlinkIfNeeded(project, file.viewProvider.virtualFile))
          annotation.aspects.find { it.id == AUTHOR }
        }
        else -> null
      }

      val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()

      val traverser = SyntaxTraverser.psiTraverser(file)
      for (element in traverser.preOrderDfsTraversal()) {
        val elementContext: VcsCodeVisionLanguageContext?
        val language: Language
        if (fileContext != null) {
          elementContext = fileContext
          language = fileLanguage
        }
        else {
          language = element.language
          elementContext = VcsCodeVisionLanguageContext.providersExtensionPoint.forLanguage(language)
          if (!additionalContexts.contains(elementContext)) {
            continue
          }
        }

        if (elementContext != null && elementContext.isAccepted(element)) {
          val textRange = InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
          val length = editor.document.textLength
          val adjustedRange = TextRange(min(textRange.startOffset, length), min(textRange.endOffset, length))
          val trimmedRange = elementContext.computeEffectiveRange(element)
          val adjustedTrimmedRange = TextRange(min(trimmedRange.startOffset, length), min(trimmedRange.endOffset, length))
          val codeAuthorInfo = PREVIEW_INFO_KEY.get(editor) ?: getCodeAuthorInfo(element.project, adjustedTrimmedRange, editor, aspect)
          val text = codeAuthorInfo.getText()
          val icon = if (codeAuthorInfo.mainAuthor != null) AllIcons.Vcs.Author else null
          val clickHandler = CodeAuthorClickHandler(element, language)
          val entry = ClickableTextCodeVisionEntry(text, id, onClick = clickHandler, icon, text, text, emptyList())
          entry.showInMorePopup = false
          lenses.add(adjustedRange to entry)
        }
      }
      return@computeCodeVisionUnderReadAction CodeVisionState.Ready(lenses)
    }
  }

  private sealed class LoadingFileAnnotation {
    class Loaded(val annotation: FileAnnotation?) : LoadingFileAnnotation()
    object NotReady : LoadingFileAnnotation()
  }

  private fun getAnnotation(editor: Editor, project: Project, document: Document): AnnotationResult<FileAnnotation?> {
    val fromUserData = editor.getUserData(VCS_CODE_AUTHOR_ANNOTATION)
    if (fromUserData != null) return AnnotationResult.Success(fromUserData)

    val virtualFile = FileDocumentManager.getInstance().getFile(document)?.let { VcsUtil.resolveSymlinkIfNeeded(project, it) }
                      ?: return AnnotationResult.NoAnnotation
    val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(virtualFile)?.takeIf { "Git" == it.name }
              ?: return AnnotationResult.NoAnnotation
    val annotationProvider = vcs.annotationProvider as? CacheableAnnotationProvider
                             ?: return AnnotationResult.NoAnnotation
    val status = FileStatusManager.getInstance(project).getStatus(virtualFile)

    return when (status) {
      FileStatus.UNKNOWN, FileStatus.IGNORED -> AnnotationResult.NoAnnotation
      FileStatus.ADDED -> AnnotationResult.Success(null) // new files have no annotation
      else -> getAnnotation(annotationProvider, virtualFile)
    }
  }

  override fun getPlaceholderCollector(editor: Editor, psiFile: PsiFile?): CodeVisionPlaceholderCollector? {
    if (psiFile == null) return null
    val language = psiFile.language
    val project = editor.project ?: return null
    val visionLanguageContext = VcsCodeVisionLanguageContext.providersExtensionPoint.forLanguage(language) ?: return null
    val virtualFile = psiFile.virtualFile
    if (virtualFile == null) return null
    val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(virtualFile) ?: return null
    if ("Git" != vcs.name) {
      return null
    }
    if (vcs.annotationProvider !is CacheableAnnotationProvider) return null
    return object : BypassBasedPlaceholderCollector {
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

private fun getCodeAuthorInfo(project: Project, range: TextRange, editor: Editor, authorAspect: LineAnnotationAspect?): VcsCodeAuthorInfo {
  if (authorAspect == null) {
    return VcsCodeAuthorInfo.NEW_CODE
  }
  val document = editor.document
  val startLine = document.getLineNumber(range.startOffset)
  val endLine = document.getLineNumber(range.endOffset)
  val provider = UpToDateLineNumberProviderImpl(document, project)

  val authorsFrequency = (startLine..endLine)
    .filterNot { document.getText(DocumentUtil.getLineTextRange(document, it)).isBlank() }
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

private val VCS_CODE_AUTHOR_ANNOTATION = Key.create<FileAnnotation>("Vcs.CodeAuthor.Annotation")

private fun getAnnotation(annotationProvider: CacheableAnnotationProvider, file: VirtualFile): AnnotationResult<FileAnnotation?> {
  val annotation = annotationProvider.getFromCache(file) ?: return AnnotationResult.NotReady
  return AnnotationResult.Success(annotation)
}

private fun handleAnnotationRegistration(annotation: FileAnnotation, editor: Editor, project: Project, file: VirtualFile) {
  if (editor.getUserData(VCS_CODE_AUTHOR_ANNOTATION) != null) return

  val annotationDisposable = Disposable {
    unregisterAnnotation(annotation)
    annotation.dispose()
  }
  annotation.setCloser {
    editor.putUserData(VCS_CODE_AUTHOR_ANNOTATION, null)
    Disposer.dispose(annotationDisposable)

    project.service<AnnotationsPreloader>().schedulePreloading(file)
  }
  annotation.setReloader { annotation.close() }

  editor.putUserData(VCS_CODE_AUTHOR_ANNOTATION, annotation)
  registerAnnotation(annotation)
  ApplicationManager.getApplication().invokeLater {
    EditorUtil.disposeWithEditor(editor, annotationDisposable)
  }
}

private fun registerAnnotation(annotation: FileAnnotation) =
  ProjectLevelVcsManager.getInstance(annotation.project).annotationLocalChangesListener.registerAnnotation(annotation)

private fun unregisterAnnotation(annotation: FileAnnotation) =
  ProjectLevelVcsManager.getInstance(annotation.project).annotationLocalChangesListener.unregisterAnnotation(annotation)

private val VcsCodeAuthorInfo.isMultiAuthor: Boolean get() = otherAuthorsCount > 0

private fun VcsCodeAuthorInfo.getText(): @NlsSafe String {
  val mainAuthorText = ShortNameType.shorten(mainAuthor, ShortNameType.NONE)

  return when {
    mainAuthorText == null -> VcsBundle.message("label.new.code")
    isMultiAuthor && isModified -> VcsBundle.message("label.multi.author.modified.code", mainAuthorText, otherAuthorsCount)
    isMultiAuthor && !isModified -> VcsBundle.message("label.multi.author.not.modified.code", mainAuthorText, otherAuthorsCount)
    !isMultiAuthor && isModified -> VcsBundle.message("label.single.author.modified.code", mainAuthorText)
    else -> mainAuthorText
  }
}

private sealed class AnnotationResult<out T> {
  class Success<T>(val res: T) : AnnotationResult<T>()
  object NotReady : AnnotationResult<Nothing>()
  object NoAnnotation : AnnotationResult<Nothing>()
}

private val PREVIEW_INFO_KEY = Key.create<VcsCodeAuthorInfo>("preview.author.info")

private fun addPreviewInfo(editor: Editor) {
  editor.putUserData(PREVIEW_INFO_KEY, VcsCodeAuthorInfo("John Smith", 2, false))
}

private fun hasPreviewInfo(editor: Editor) = PREVIEW_INFO_KEY.get(editor) != null

internal class VcsCodeAuthorInfo(val mainAuthor: String?, val otherAuthorsCount: Int, val isModified: Boolean) {
  companion object {
    val NEW_CODE: VcsCodeAuthorInfo = VcsCodeAuthorInfo(null, 0, true)
  }
}
