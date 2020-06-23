// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.FileNavigationLogger
import com.intellij.filePrediction.references.FilePredictionReferencesHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class FileUsagePredictionHandler(private val candidatesLimit: Int,
                                          private val logTopLimit: Int,
                                          private val logTotalLimit: Int) {
  private val predictor: FileUsagePredictor = FileUsagePredictorProvider.getFileUsagePredictor()

  fun predictNextFile(project: Project, sessionId: Int, file: VirtualFile) {
    val start = System.currentTimeMillis()
    val refs = FilePredictionReferencesHelper.calculateExternalReferences(project, file)

    val candidatesToCalculate = if (!predictor.isDummy) candidatesLimit else logTotalLimit
    val candidates = predictor.predictNextFile(project, file, refs, candidatesToCalculate)
    if (candidates.isNotEmpty()) {
      val duration = System.currentTimeMillis() - start
      logCandidatesWithProbability(project, sessionId, file.path, candidates, duration, refs.duration)
    }
  }

  private fun logCandidatesWithProbability(project: Project,
                                           sessionId: Int,
                                           prevPath: String?,
                                           candidates: List<FilePredictionCandidate>,
                                           duration: Long,
                                           refsComputation: Long) {
    val head = candidates.take(logTopLimit)
    logCalculatedCandidates(project, sessionId, prevPath, head, duration, refsComputation)

    if (candidates.size > logTopLimit) {
      val tail = candidates.subList(logTopLimit, candidates.size)
      val randomToLog = tail.shuffled().take(logTotalLimit - logTopLimit)
      logCalculatedCandidates(project, sessionId, prevPath, randomToLog, duration, refsComputation)
    }
  }

  private fun logCalculatedCandidates(project: Project,
                                      sessionId: Int,
                                      prevPath: String?,
                                      candidates: Collection<FilePredictionCandidate>,
                                      totalDuration: Long,
                                      refsComputation: Long) {
    for (candidate in candidates) {
      val probability = candidate.probability
      val features = candidate.features
      FileNavigationLogger.logEvent(
        project, "candidate.calculated", sessionId, features, candidate.path, prevPath,
        totalDuration, refsComputation, candidate.duration, probability
      )
    }
  }
}

