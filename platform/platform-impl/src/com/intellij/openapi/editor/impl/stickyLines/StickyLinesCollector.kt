// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
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
    private val LOG = logger<StickyLinesCollector>()

    private val STICKY_LINES_MOD_STAMP_KEY: Key<Long> = Key.create("editor.sticky.lines.mod.stamp")

    fun isModStampChanged(psiFile: PsiFile): Boolean {
      val prevModStamp = psiFile.getUserData(STICKY_LINES_MOD_STAMP_KEY)
      val currModStamp = psiFile.modificationStamp
      LOG.trace {
        "file: ${psiFile.name}, modStampChanged: ${prevModStamp != currModStamp}, prev: $prevModStamp, curr: $currModStamp"
      }
      return prevModStamp != currModStamp
    }

    fun updatesModStamp(psiFile: PsiFile) {
      psiFile.putUserData(STICKY_LINES_MOD_STAMP_KEY, psiFile.modificationStamp)
      LOG.trace {
        "psiFile: ${psiFile.name}, updateStamp: ${psiFile.modificationStamp}"
      }
    }

    fun resetModStamp(psiFile: PsiFile) {
      psiFile.putUserData(STICKY_LINES_MOD_STAMP_KEY, null)
      LOG.trace {
        "psiFile: ${psiFile.name}, resetStamp"
      }
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  fun forceCollectPass() {
    ThreadingAssertions.assertReadAccess()
    ThreadingAssertions.assertBackgroundThread()

    val psiFile = PsiDocumentManager.getInstance(project).getCachedPsiFile(document)
    if (psiFile != null) {
      resetModStamp(psiFile)
    } else {
      LOG.debug {
        "file: ${fileName()}, forceCollect: failed"
      }
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
    LOG.debug {
      "file: ${vFile.name}, collectedInfos: ${infos.size}"
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
    LOG.debug {
      "file: ${fileName()}, appliedLines: ${lines.size}, added: ${linesToAdd.size}, outdated: ${outdatedLines.size}"
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

  private fun fileName(): String {
    return FileDocumentManager.getInstance().getFile(document)?.name ?: "UNKNOWN"
  }

  private fun debugText(element: PsiElement): String? {
    return if (Registry.`is`("editor.show.sticky.lines.debug")) {
      element.toString()
    } else {
      null
    }
  }
}
