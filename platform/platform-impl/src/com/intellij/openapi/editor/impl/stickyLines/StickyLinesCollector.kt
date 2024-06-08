// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xml.breadcrumbs.PsiFileBreadcrumbsCollector
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Responsible for collecting sticky lines based on psi file structure.
 * The implementation relies on PsiFileBreadcrumbsCollector to collect psi elements at specific document line.
 */
@Internal
class StickyLinesCollector(private val project: Project, private val document: Document) {

  companion object {
    private val STICKY_LINES_MOD_STAMP_KEY: Key<Long> = Key.create("editor.sticky.lines.mod.stamp")

    fun isModStampChanged(psiFile: PsiFile): Boolean {
      val prevModStamp = psiFile.getUserData(STICKY_LINES_MOD_STAMP_KEY)
      val currModStamp = psiFile.modificationStamp
      return prevModStamp != currModStamp
    }

    fun updatesModStamp(psiFile: PsiFile) {
      psiFile.putUserData(STICKY_LINES_MOD_STAMP_KEY, psiFile.modificationStamp)
    }

    fun resetModStamp(psiFile: PsiFile) {
      psiFile.putUserData(STICKY_LINES_MOD_STAMP_KEY, null)
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  fun forceCollectPass() {
    ThreadingAssertions.assertReadAccess()
    ThreadingAssertions.assertBackgroundThread()

    PsiDocumentManager.getInstance(project).getCachedPsiFile(document)?.let { psiFile ->
      resetModStamp(psiFile)
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  fun collectLines(vFile: VirtualFile, progress: ProgressIndicator): Collection<StickyLineInfo> {
    ThreadingAssertions.assertReadAccess()
    ThreadingAssertions.assertBackgroundThread()

    val psiCollector = PsiFileBreadcrumbsCollector(project)
    val infos: MutableSet<StickyLineInfo> = mutableSetOf()
    val lineCount: Int = document.getLineCount()
    for (line in 0 until lineCount) {
      progress.checkCanceled()
      val endOffset: Int = document.getLineEndOffset(line)
      val psiElements: List<PsiElement> = psiCollector.computePsiElements(vFile, document, endOffset)
      for (element: PsiElement in psiElements) {
        infos.add(StickyLineInfo(
          element.textOffset,
          element.textRange.endOffset,
          debugText(element)
        ))
      }
    }
    return infos
  }

  @RequiresEdt
  fun applyLines(lines: Collection<StickyLineInfo>) {
    ThreadingAssertions.assertEventDispatchThread()

    val stickyModel: StickyLinesModel = StickyLinesModel.getModel(project, document) ?: return
    val linesToAdd: MutableSet<StickyLineInfo> = HashSet(lines)
    val outdatedLines: List<StickyLine> = mergeWithExistingLines(stickyModel, linesToAdd) // mutates linesToAdd
    for (toRemove: StickyLine in outdatedLines) {
      stickyModel.removeStickyLine(toRemove)
    }
    for (toAdd: StickyLineInfo in linesToAdd) {
      stickyModel.addStickyLine(toAdd.textOffset, toAdd.endOffset, toAdd.debugText)
    }
    stickyModel.notifyListeners()
  }

  private fun mergeWithExistingLines(
    stickyModel: StickyLinesModel,
    linesToAdd: MutableSet<StickyLineInfo>,
  ): List<StickyLine> {
    val outdatedLines: MutableList<StickyLine> = mutableListOf()
    stickyModel.processStickyLines(StickyLinesModel.SourceID.IJ) { existingLine: StickyLine ->
      val existing = StickyLineInfo(existingLine.textRange())
      val keepExisting = linesToAdd.remove(existing)
      if (!keepExisting) {
        outdatedLines.add(existingLine)
      }
      true
    }
    return outdatedLines
  }

  private fun debugText(element: PsiElement): String? {
    return if (Registry.`is`("editor.show.sticky.lines.debug")) {
      element.toString()
    } else {
      null
    }
  }
}
