// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.xml.breadcrumbs.PsiFileBreadcrumbsCollector
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Responsible for collecting sticky lines based on psi file structure.
 * The implementation relies on PsiFileBreadcrumbsCollector to collect psi elements at specific document line.
 */
@Internal
class StickyLinesCollector(private val project: Project, private val document: Document) {

  fun collectLines(vFile: VirtualFile): MutableSet<StickyLineInfo> {
    ThreadingAssertions.assertReadAccess()
    ThreadingAssertions.assertBackgroundThread()
    val psiCollector = PsiFileBreadcrumbsCollector(project)
    val infos: MutableSet<StickyLineInfo> = mutableSetOf()
    val lineCount: Int = document.getLineCount()
    for (line in 0 until lineCount) {
      ProgressManager.checkCanceled()
      val endOffset: Int = document.getLineEndOffset(line)
      val psiElements: List<PsiElement> = psiCollector.computePsiElements(vFile, document, endOffset)
      for (element: PsiElement in psiElements) {
        if (!isInBanList(element)) {
          infos.add(StickyLineInfo(
            element.textOffset,
            element.textRange.endOffset,
            debugText(element)
          ))
        }
      }
    }
    return infos
  }

  fun applyLines(linesToAdd: MutableSet<StickyLineInfo>) {
    ThreadingAssertions.assertEventDispatchThread()
    val stickyModel: StickyLinesModel = StickyLinesModel.getModel(project, document) ?: return
    val outdatedLines: List<StickyLine> = findOutdatedLines(stickyModel, linesToAdd) // mutates linesToAdd
    for (toRemove: StickyLine in outdatedLines) {
      stickyModel.removeStickyLine(toRemove)
    }
    for (toAdd: StickyLineInfo in linesToAdd) {
      stickyModel.addStickyLine(toAdd.textOffset, toAdd.endOffset, toAdd.debugText)
    }
    stickyModel.notifyListeners()
  }

  private fun findOutdatedLines(stickyModel: StickyLinesModel, linesToAdd: MutableSet<StickyLineInfo>): List<StickyLine> {
    val outdatedLines: MutableList<StickyLine> = mutableListOf()
    stickyModel.processStickyLines { line: StickyLine ->
      val existingLine = StickyLineInfo(line.textRange())
      val keepExisting = linesToAdd.remove(existingLine)
      if (!keepExisting) {
        outdatedLines.add(line)
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

  private fun isInBanList(element: PsiElement): Boolean {
    // TODO: provide extension point for suppressing/including psi elements
    if (element.language.displayName == "Kotlin") {
      // special handling for kotlin to exclude if/else, try/catch
      // see org.jetbrains.kotlin.idea.codeInsight.KotlinBreadcrumbsInfoProvider.Holder,
      // org.jetbrains.kotlin.KtNodeTypes.THEN, org.jetbrains.kotlin.KtNodeTypes.ELSE, etc
      val debugName = element.toString()
      return when (debugName) {
        "THEN", "ELSE", "WHEN", "FINALLY" -> true
        "BLOCK", "BODY" -> when (element.parent?.toString()) {
          "TRY", "FOR", "DO_WHILE", "WHEN_ENTRY" -> true
          else -> false
        }
        else -> false
      }
    }
    return false
  }
}
