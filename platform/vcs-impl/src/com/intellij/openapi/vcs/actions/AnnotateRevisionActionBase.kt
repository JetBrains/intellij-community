// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsHistoryUtil
import com.intellij.openapi.vcs.impl.BackgroundableActionLock
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.progress.ProgressUIUtil
import com.intellij.util.diff.Diff
import com.intellij.util.diff.FilesTooBigForDiffException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import javax.swing.Icon

internal abstract class AnnotateRevisionActionBase : DumbAwareAction {
  constructor(
    dynamicText: Supplier<String>,
    dynamicDescription: Supplier<String>,
    icon: Icon?
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

    annotate(file, fileRevision, vcs, getEditor(e), getAnnotatedLine(e))
  }

  companion object {
    @JvmStatic
    fun isEnabled(
      vcs: AbstractVcs?,
      file: VirtualFile?,
      fileRevision: VcsFileRevision?
    ): Boolean {
      if (file == null || vcs == null || fileRevision == null || VcsHistoryUtil.isEmpty(fileRevision)) return false
      val provider = vcs.annotationProvider ?: return false
      if (!provider.isAnnotationValid(fileRevision)) return false

      return !VcsAnnotateUtil.getBackgroundableLock(vcs.project, file).isLocked
    }

    @JvmStatic
    fun annotate(
      file: VirtualFile,
      fileRevision: VcsFileRevision,
      vcs: AbstractVcs,
      editor: Editor?,
      annotatedLine: Int
    ) {
      val annotationProvider = vcs.annotationProvider ?: return
      val oldContent = editor?.document?.getImmutableCharSequence()

      val fileAnnotationRef = Ref<FileAnnotation>()
      val newLineRef = Ref<Int>()
      val exceptionRef = Ref<VcsException>()

      val actionLock: BackgroundableActionLock = VcsAnnotateUtil.getBackgroundableLock(vcs.project, file)
      actionLock.lock()

      val semaphore = Semaphore(0)
      val shouldOpenEditorInSync = AtomicBoolean(true)

      ProgressManager.getInstance().run(object : Task.Backgroundable(vcs.project, VcsBundle.message("retrieving.annotations"), true) {
        override fun run(indicator: ProgressIndicator) {
          try {
            val fileAnnotation = annotationProvider.annotate(file, fileRevision)

            val newLine = if (annotatedLine < 0) -1 else translateLine(oldContent, fileAnnotation.annotatedContent, annotatedLine)

            fileAnnotationRef.set(fileAnnotation)
            newLineRef.set(newLine)

            shouldOpenEditorInSync.set(false)
            semaphore.release()
          }
          catch (e: VcsException) {
            exceptionRef.set(e)
          }
        }

        override fun onFinished() {
          actionLock.unlock()
        }

        override fun onSuccess() {
          if (!exceptionRef.isNull) {
            AbstractVcsHelper.getInstance(project).showError(exceptionRef.get(), VcsBundle.message("operation.name.annotate"))
          }
          if (fileAnnotationRef.isNull) return

          AbstractVcsHelper.getInstance(project).showAnnotation(fileAnnotationRef.get(), file, vcs, newLineRef.get())
        }
      })

      try {
        semaphore.tryAcquire(ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS, TimeUnit.MILLISECONDS)

        // We want to let Backgroundable task open editor if it was fast enough.
        // This will remove blinking on editor opening (step 1 - editor opens, step 2 - annotations are shown).
        if (shouldOpenEditorInSync.get()) {
          val content = LoadTextUtil.loadText(file)
          val newLine = translateLine(oldContent, content, annotatedLine)

          val openFileDescriptor = OpenFileDescriptor(vcs.project, file, newLine, 0)
          FileEditorManager.getInstance(vcs.project).openTextEditor(openFileDescriptor, true)
        }
      }
      catch (_: InterruptedException) {
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
