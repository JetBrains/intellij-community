// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.ExternalReferencesResult.Companion.FAILED_COMPUTATION
import com.intellij.filePrediction.history.FilePredictionHistory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.NonUrgentExecutor

internal object FileUsagePredictor {
  private const val CALCULATE_OPEN_FILE_PROBABILITY: Double = 0.5

  private const val CALCULATE_CANDIDATE_PROBABILITY: Double = 0.1
  private const val MAX_CANDIDATE: Int = 10

  fun onFileOpened(project: Project, newFile: VirtualFile, prevFile: VirtualFile?) {
    NonUrgentExecutor.getInstance().execute {
      if (Math.random() < CALCULATE_OPEN_FILE_PROBABILITY) {
        logFileFeatures(project, newFile, prevFile)
      }
      FilePredictionHistory.getInstance(project).onFileOpened(newFile.url)
    }
  }

  private fun logFileFeatures(project: Project, newFile: VirtualFile, prevFile: VirtualFile?) {
    val start = System.currentTimeMillis()
    val result = calculateExternalReferences(project, prevFile)
    val refsComputation = System.currentTimeMillis() - start

    FileNavigationLogger.logEvent(project, newFile, prevFile, "file.opened", refsComputation, result.contains(newFile))
    if (Math.random() < CALCULATE_CANDIDATE_PROBABILITY) {
      prevFile?.let {
        calculateCandidates(project, it, newFile, refsComputation, result)
      }
    }
  }

  private fun calculateExternalReferences(project: Project, prevFile: VirtualFile?): ExternalReferencesResult {
    return ApplicationManager.getApplication().runReadAction(Computable {
      if (prevFile?.isValid == false) {
        return@Computable FAILED_COMPUTATION
      }

      val prevPsiFile = prevFile?.let { PsiManager.getInstance(project).findFile(it) }
      if (DumbService.isDumb(project)) {
        return@Computable FAILED_COMPUTATION
      }
      FilePredictionFeaturesHelper.calculateExternalReferences(prevPsiFile)
    })
  }

  private fun calculateCandidates(project: Project,
                                  prevFile: VirtualFile,
                                  openedFile: VirtualFile,
                                  refsComputation: Long,
                                  referencesResult: ExternalReferencesResult) {
    val candidates = selectFileCandidates(project, prevFile, referencesResult.references)
    for (candidate in candidates) {
      if (candidate != openedFile) {
        FileNavigationLogger.logEvent(project, candidate, prevFile, "candidate.calculated", refsComputation, referencesResult.contains(candidate))
      }
    }
  }

  private fun selectFileCandidates(project: Project, currentFile: VirtualFile, refs: Set<VirtualFile>): List<VirtualFile> {
    return ApplicationManager.getApplication().runReadAction(Computable {
      val result = ArrayList<VirtualFile>()
      addWithLimit(refs.iterator(), result, currentFile, MAX_CANDIDATE / 2)

      val fileIndex = FileIndexFacade.getInstance(project)
      var parent = currentFile.parent
      while (parent != null && parent.isDirectory && result.size < MAX_CANDIDATE && fileIndex.isInProjectScope(parent)) {
        addWithLimit(parent.children.iterator(), result, currentFile, MAX_CANDIDATE)
        parent = parent.parent
      }
      result
    })
  }

  private fun addWithLimit(from: Iterator<VirtualFile>, to: MutableList<VirtualFile>, skip: VirtualFile, limit: Int) {
    while (to.size < limit && from.hasNext()) {
      val next = from.next()
      if (!next.isDirectory && skip != next) {
        to.add(next)
      }
    }
  }
}

