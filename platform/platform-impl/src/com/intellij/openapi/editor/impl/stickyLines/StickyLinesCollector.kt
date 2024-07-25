// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
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

  object ModStamp {
    private val STICKY_LINES_MOD_STAMP_KEY: Key<Long> = Key.create("editor.sticky.lines.mod.stamp")
    private val STICKY_LINES_FIRST_PASS_FOR_EDITOR: Key<Boolean> = Key.create("editor.sticky.lines.first.pass")

    fun isChanged(editor: Editor, psiFile: PsiFile): Boolean {
      val isFirstPass: Boolean = editor.getUserData(STICKY_LINES_FIRST_PASS_FOR_EDITOR) == null
      if (isFirstPass) {
        // always run pass on editor opening IJPL-158818
        LOG.trace { "first pass for editor ${debugPsiFile(psiFile)}" }
        editor.putUserData(STICKY_LINES_FIRST_PASS_FOR_EDITOR, false)
        if (psiFile.getUserData(STICKY_LINES_MOD_STAMP_KEY) != null) {
          reset(psiFile)
        }
        return true
      }
      val prevModStamp: Long? = psiFile.getUserData(STICKY_LINES_MOD_STAMP_KEY)
      val currModStamp: Long = modStamp(psiFile)
      LOG.trace { "checking modStamp: ${traceStampChanged(psiFile, prevModStamp, currModStamp)}" }
      return prevModStamp != currModStamp
    }

    internal fun update(psiFile: PsiFile) {
      val modStamp: Long = modStamp(psiFile)
      psiFile.putUserData(STICKY_LINES_MOD_STAMP_KEY, modStamp)
      LOG.trace { "updating modStamp=$modStamp for ${debugPsiFile(psiFile)}" }
    }

    internal fun reset(psiFile: PsiFile) {
      psiFile.putUserData(STICKY_LINES_MOD_STAMP_KEY, null)
      LOG.trace { "resetting modStamp for ${debugPsiFile(psiFile)}" }
    }

    /**
     * Deliberately uses both psiFile and document.
     *
     * Effectively psiFile and document are weakly referenced.
     * Currently, sticky lines are stored in a document markup model, which is user data of the document.
     * Tracking only psiFile stamp is not enough because psiFile and document can be collected by GC independent.
     * If the psiFile is not collected but the document is collected, then on the next editor opening
     * there will be the same psi modification stamp but empty document markup model.
     * In this case, the sticky lines pass should be triggered even though the mod stamp is not changed.
     * Otherwise, an empty sticky lines panel will be shown until someone changes the psiFile.
     * To address this issue, mod stamp takes into account the document stamp
     * so if the document markup model is recreated then the pass is triggered
     */
    private fun modStamp(psiFile: PsiFile): Long {
      return psiFile.modificationStamp + psiFile.fileDocument.modificationStamp
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  fun forceCollectPass() {
    ThreadingAssertions.assertReadAccess(); ThreadingAssertions.assertBackgroundThread()

    val psiFile: PsiFile? = PsiDocumentManager.getInstance(project).getCachedPsiFile(document)
    if (psiFile != null) {
      ModStamp.reset(psiFile)
    } else if (LOG.isDebugEnabled) {
      val fileName: String? = FileDocumentManager.getInstance().getFile(document)?.name
      LOG.debug("cannot find psi file for ${fileName ?: "UNKNOWN"}")
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  fun collectLines(vFile: VirtualFile, progress: ProgressIndicator): Collection<StickyLineInfo> {
    ThreadingAssertions.assertReadAccess(); ThreadingAssertions.assertBackgroundThread()

    val psiCollector = PsiFileBreadcrumbsCollector(project)
    val infos: MutableSet<StickyLineInfo> = HashSet()
    val lineCount: Int = document.getLineCount()
    for (line in 0 until lineCount) {
      progress.checkCanceled()
      val endOffset: Int = document.getLineEndOffset(line)
      val psiElements: List<PsiElement> = psiCollector.computePsiElements(vFile, document, endOffset)
      for (element: PsiElement in psiElements) {
        infos.add(StickyLineInfo(
          element.textOffset,
          element.textRange.endOffset,
          debugText(element),
        ))
      }
    }
    LOG.debug { "total lines collected: ${infos.size} for ${fileName(vFile)}" }
    return infos
  }

  @RequiresEdt
  fun applyLines(psiFile: PsiFile, lines: Collection<StickyLineInfo>) {
    ThreadingAssertions.assertEventDispatchThread()

    ModStamp.update(psiFile)
    val stickyModel: StickyLinesModel = stickyLinesModel(psiFile) ?: return
    val linesToAdd: MutableSet<StickyLineInfo> = HashSet(lines)
    val outdatedLines: List<StickyLine> = mergeWithExistingLines(stickyModel, linesToAdd)
    for (toRemove: StickyLine in outdatedLines) {
      stickyModel.removeStickyLine(toRemove)
    }
    for (toAdd: StickyLineInfo in linesToAdd) {
      stickyModel.addStickyLine(toAdd.textOffset, toAdd.endOffset, toAdd.debugText)
    }
    LOG.debug {
      "total lines applied: ${lines.size}" +
      ", new added: ${linesToAdd.size}" +
      ", old removed: ${outdatedLines.size}" +
      ", ${debugPsiFile(psiFile)}"
    }
    stickyModel.notifyLinesUpdate()
  }

  private fun stickyLinesModel(psiFile: PsiFile): StickyLinesModel? {
    val stickyModel: StickyLinesModel? = StickyLinesModel.getModel(project, document)
    if (stickyModel == null) {
      ModStamp.reset(psiFile)
      LOG.error(
        "sticky lines model does not exist while applying" +
        " collected lines for ${debugPsiFile(psiFile)}"
      )
      return null
    }
    return stickyModel
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

  private fun fileName(vFile: VirtualFile): String {
    val psiFile: PsiFile? = PsiDocumentManager.getInstance(project).getPsiFile(document)
    return psiFile?.let { debugPsiFile(it) } ?: vFile.name
  }

  private fun debugText(element: PsiElement): String? {
    return if (Registry.`is`("editor.show.sticky.lines.debug")) {
      element.toString()
    } else {
      null
    }
  }

  companion object {
    private val LOG: Logger = logger<StickyLinesCollector>()

    private fun traceStampChanged(
      psiFile: PsiFile,
      prevModStamp: Long?,
      currModStamp: Long,
    ): String {
      val isChanged = prevModStamp != currModStamp
      val stamp = if (isChanged) {
        "prevStamp=$prevModStamp, currStamp=$currModStamp"
      } else {
        "stamp=$currModStamp"
      }
      return "isChange=$isChanged, $stamp, ${debugPsiFile(psiFile)}"
    }

    private fun debugPsiFile(psiFile: PsiFile): String {
      val fileName = psiFile.name
      val psiFileId = Integer.toHexString(System.identityHashCode(psiFile))
      val documentId = Integer.toHexString(System.identityHashCode(psiFile.fileDocument))
      val psiFileStamp = psiFile.modificationStamp
      val documentStamp = psiFile.fileDocument.modificationStamp
      return "$fileName[psiId=@$psiFileId, psiStamp=$psiFileStamp, docId=@$documentId, docStamp=$documentStamp]"
    }
  }
}
