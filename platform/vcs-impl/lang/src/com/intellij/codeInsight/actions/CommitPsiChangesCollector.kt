// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vcs.changes.Change
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object CommitPsiChangesCollector {
  /**
   * Creates light files for before and after contents and returns changed PSI elements.
   */
  suspend fun <T : PsiElement> getChangedElements(change: Change, elementExtractor: PsiCollector<T>): List<T> {
    val beforeRevision = change.beforeRevision
    val afterRevision = change.afterRevision

    val contentBefore = withContext(Dispatchers.IO) { VcsFacadeImpl.getRevisionedContentFrom(beforeRevision) }
    val contentAfter = withContext(Dispatchers.IO) { VcsFacadeImpl.getRevisionedContentFrom(afterRevision) }

    return readAction {
      val elementsBefore = if (beforeRevision != null && contentBefore != null)
        elementExtractor.collectTargetPsi(contentBefore, beforeRevision.getFile().getFileType())
      else
        mutableListOf()

      val elementsAfter = if (afterRevision != null && contentAfter != null)
        elementExtractor.collectTargetPsi(contentAfter, afterRevision.getFile().getFileType())
      else
        mutableListOf()

      if (elementsBefore.isEmpty() && elementsAfter.isEmpty()) return@readAction listOf()

      if (change.getType() == Change.Type.NEW) {
        return@readAction elementsAfter
      }
      if (change.getType() == Change.Type.DELETED) {
        return@readAction elementsBefore
      }

      contentBefore!!
      contentAfter!!

      val ranges = VcsFacadeImpl.getChangedRangesForCommit(contentBefore, contentAfter)
      val changedLinesBefore = VcsFacadeImpl.createLinesBitSetBefore(ranges)
      val changedLinesAfter = VcsFacadeImpl.createLinesBitSetAfter(ranges)

      val changedPsiBefore = VcsFacadeImpl.filterChanged(elementsBefore, changedLinesBefore)
      val changedPsiAfter = VcsFacadeImpl.filterChanged(elementsAfter, changedLinesAfter)
      ContainerUtil.concat(changedPsiBefore, changedPsiAfter)
    }
  }
}
