// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.annotate.AnnotationProvider
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsHistoryUtil
import com.intellij.openapi.vcs.vfs.VcsVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.progress.ProgressUIUtil
import com.intellij.util.diff.Diff
import com.intellij.util.diff.FilesTooBigForDiffException
import com.intellij.vcs.VcsDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.function.Supplier
import javax.swing.Icon
import kotlin.time.Duration.Companion.milliseconds

internal abstract class AnnotateRevisionActionBase : DumbAwareAction {
  constructor(
    dynamicText: Supplier<String>,
    dynamicDescription: Supplier<String>,
    icon: Icon?,
  ) : super(dynamicText, dynamicDescription, icon)

  protected abstract fun getVcs(e: AnActionEvent): AbstractVcs?

  protected abstract fun getFile(e: AnActionEvent): VirtualFile?

  protected abstract fun getFileRevision(e: AnActionEvent): VcsFileRevision?

  protected open fun getEditor(e: AnActionEvent): Editor? = null

  protected abstract fun getAnnotatedLine(e: AnActionEvent): Int

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = isEnabled(e)
  }

  fun isEnabled(e: AnActionEvent): Boolean {
    if (e.project == null) return false

    return isEnabled(getVcs(e), getFile(e), getFileRevision(e))
  }

  override fun actionPerformed(e: AnActionEvent) {
    val fileRevision = getFileRevision(e)
    val file = getFile(e)
    val vcs = getVcs(e)
    if (fileRevision == null || file == null || vcs == null) return
    annotateAsync(file, fileRevision, vcs, getEditor(e), getAnnotatedLine(e))
  }

  companion object {
    @JvmStatic
    fun isEnabled(
      vcs: AbstractVcs?,
      file: VirtualFile?,
      fileRevision: VcsFileRevision?,
    ): Boolean {
      if (file == null || vcs == null || fileRevision == null || VcsHistoryUtil.isEmpty(fileRevision)) return false
      val provider = vcs.annotationProvider ?: return false
      if (!provider.isAnnotationValid(fileRevision)) return false

      return !VcsAnnotateUtil.getBackgroundableLock(vcs.project, file).isLocked
    }

    @JvmStatic
    fun annotateAsync(file: VirtualFile, fileRevision: VcsFileRevision, vcs: AbstractVcs, editor: Editor?, annotatedLine: Int) {
      VcsDisposable.getInstance(vcs.project).coroutineScope.launch {
        annotate(file, fileRevision, vcs, editor, annotatedLine)
      }
    }

    private suspend fun annotate(
      file: VirtualFile,
      fileRevision: VcsFileRevision,
      vcs: AbstractVcs,
      editor: Editor?,
      annotatedLine: Int,
    ) {
      val annotationProvider = vcs.annotationProvider ?: return
      withContext(Dispatchers.IO) {
        // Explicitly load content to ensure that it won't be loaded on EDT later.
        if (file is VcsVirtualFile) {
          file.loadContent()
        }

        val oldContent = editor?.document?.getImmutableCharSequence()

        val annotationJob = launch {
          computeAnnotation(vcs, file, annotationProvider, fileRevision, annotatedLine, oldContent)
        }

        // We want to let the background task open the editor if it was fast enough.
        // This will remove blinking on editor opening (step 1 - editor opens, step 2 - annotations are shown).
        val annotationSlow = withTimeoutOrNull(ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS.milliseconds) { annotationJob.join() } == null
        if (annotationSlow) {
          openEditor(vcs.project, file, oldContent, annotatedLine)
        }
      }
    }

    private suspend fun computeAnnotation(
      vcs: AbstractVcs,
      file: VirtualFile,
      annotationProvider: AnnotationProvider,
      fileRevision: VcsFileRevision,
      annotatedLine: Int,
      oldContent: @NlsSafe CharSequence?,
    ) {
      val project = vcs.project
      val actionLock = VcsAnnotateUtil.getBackgroundableLock(project, file)
      withContext(Dispatchers.EDT) {
        actionLock.lock()
        try {
          val (annotation, line) = withContext(Dispatchers.Default) {
            withBackgroundProgress(project, VcsBundle.message("retrieving.annotations")) {
              val fileAnnotation = annotationProvider.annotate(file, fileRevision)
              val newLine = if (annotatedLine < 0) -1 else translateLine(oldContent, fileAnnotation.annotatedContent, annotatedLine)
              fileAnnotation to newLine
            }
          }
          AbstractVcsHelper.getInstance(vcs.project).showAnnotation(annotation, file, vcs, line)
        }
        catch (e: VcsException) {
          AbstractVcsHelper.getInstance(vcs.project).showError(e, VcsBundle.message("operation.name.annotate"))
        }
        finally {
          actionLock.unlock()
        }
      }
    }

    private suspend fun openEditor(
      project: Project,
      file: VirtualFile,
      oldContent: CharSequence?,
      annotatedLine: Int,
    ) {
      val newLine = withContext(Dispatchers.IO) {
        val content = LoadTextUtil.loadText(file)
        translateLine(oldContent, content, annotatedLine)
      }

      withContext(Dispatchers.EDT) {
        val openFileDescriptor = OpenFileDescriptor(project, file, newLine, 0)
        FileEditorManager.getInstance(project).openTextEditor(openFileDescriptor, true)
      }
    }

    @JvmStatic
    private fun translateLine(oldContent: CharSequence?, newContent: CharSequence?, line: Int): Int {
      if (oldContent == null || newContent == null) return line
      return try {
        Diff.translateLine(oldContent, newContent, line, true)
      }
      catch (_: FilesTooBigForDiffException) {
        line
      }
    }
  }
}
