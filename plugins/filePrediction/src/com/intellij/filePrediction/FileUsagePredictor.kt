// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.ExternalReferencesResult.Companion.FAILED_COMPUTATION
import com.intellij.filePrediction.history.FilePredictionHistory
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.NonUrgentExecutor

object FileUsagePredictor {
  private const val CALCULATE_CANDIDATE_PROBABILITY: Double = 0.2
  private const val MAX_CANDIDATE: Int = 10

  fun onFileOpened(project: Project, newFile: VirtualFile, prevFile: VirtualFile?) {
    NonUrgentExecutor.getInstance().execute {
      val result = calculateExternalReferences(project, prevFile)

      FileNavigationLogger.logEvent(project, newFile, prevFile, "file.opened", result.contains(newFile))
      if (Math.random() < CALCULATE_CANDIDATE_PROBABILITY) {
        prevFile?.let {
          calculateCandidates(project, it, newFile, result)
        }
      }

      FilePredictionHistory.getInstance(project).onFileOpened(newFile.url)
    }
  }

  private fun calculateExternalReferences(project: Project, prevFile: VirtualFile?): ExternalReferencesResult {
    return ApplicationManager.getApplication().runReadAction(Computable<ExternalReferencesResult> {
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
                                  referencesResult: ExternalReferencesResult) {
    val candidates = selectFileCandidates(project, prevFile, referencesResult.references)
    for (candidate in candidates) {
      if (candidate != openedFile) {
        FileNavigationLogger.logEvent(project, candidate, prevFile, "candidate.calculated", referencesResult.contains(candidate))
      }
    }
  }

  private fun selectFileCandidates(project: Project, currentFile: VirtualFile, refs: Set<VirtualFile>): List<VirtualFile> {
    val result = ArrayList<VirtualFile>()
    addWithLimit(refs.iterator(), result, currentFile, MAX_CANDIDATE / 2)

    val fileIndex = FileIndexFacade.getInstance(project)
    var parent = currentFile.parent
    while (parent != null && parent.isDirectory && result.size < MAX_CANDIDATE && fileIndex.isInProjectScope(parent)) {
      addWithLimit(parent.children.iterator(), result, currentFile, MAX_CANDIDATE)
      parent = parent.parent
    }
    return result
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

private object FileNavigationLogger {
  private const val GROUP_ID = "file.prediction"

  fun logEvent(project: Project, newFile: VirtualFile, prevFile: VirtualFile?, event: String, isInRef: ThreeState) {
    val data = FileTypeUsagesCollector.newFeatureUsageData(newFile.fileType).
      addNewFileInfo(newFile, isInRef).
      addPrevFileInfo(prevFile).
      addFileFeatures(project, newFile, prevFile)

    FUCounterUsageLogger.getInstance().logEvent(project, GROUP_ID, event, data)
  }

  private fun FeatureUsageData.addNewFileInfo(newFile: VirtualFile, isInRef: ThreeState): FeatureUsageData {
    if (isInRef != ThreeState.UNSURE) {
      addData("in_ref", isInRef == ThreeState.YES)
    }
    return addAnonymizedPath(newFile.path)
  }

  private fun FeatureUsageData.addPrevFileInfo(prevFile: VirtualFile?): FeatureUsageData {
    if (prevFile != null) {
      return addData("prev_file_type", prevFile.fileType.name).addAnonymizedValue("prev_file_path", prevFile.path)
    }
    return this
  }

  private fun FeatureUsageData.addFileFeatures(project: Project,
                                               newFile: VirtualFile,
                                               prevFile: VirtualFile?): FeatureUsageData {
    val features = FilePredictionFeaturesHelper.calculateFileFeatures(project, newFile, prevFile)
    for (feature in features) {
      feature.value.addToEventData(feature.key, this)
    }
    return this
  }
}

