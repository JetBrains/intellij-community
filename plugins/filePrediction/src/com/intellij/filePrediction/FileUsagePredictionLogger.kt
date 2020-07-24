// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.predictor.FilePredictionCandidate
import com.intellij.openapi.project.Project

internal class FileUsagePredictionLogger(private val logTopLimit: Int, private val logTotalLimit: Int) {

  fun logOpenedFile(project: Project, sessionId: Int, prevPath: String?, candidate: FilePredictionCandidate, total: Long, refs: Long) {
    logCalculatedCandidate(project, sessionId, prevPath, candidate, total, refs, true)
  }

  fun logNotOpenedCandidates(project: Project, sessionId: Int, prevPath: String?, candidates: List<FilePredictionCandidate>, total: Long, refs: Long) {
    val head = candidates.take(logTopLimit)
    for (candidate in head) {
      logCalculatedCandidate(project, sessionId, prevPath, candidate, total, refs, false)
    }

    val numOfRecentCandidates = head.filter { isRecentCandidate(it) }.count()
    if (candidates.size > logTopLimit) {
      var tail = candidates.subList(logTopLimit, candidates.size)
      if (numOfRecentCandidates > logTopLimit / 2) {
        tail = tail.filter { !isRecentCandidate(it) }
      }

      val randomToLog = tail.shuffled().take(logTotalLimit - logTopLimit)
      for (candidate in randomToLog) {
        logCalculatedCandidate(project, sessionId, prevPath, candidate, total, refs, false)
      }
    }
  }

  private fun isRecentCandidate(candidate: FilePredictionCandidate) = candidate.source == "open" || candidate.source == "recent"

  private fun logCalculatedCandidate(project: Project,
                                     sessionId: Int,
                                     prevPath: String?,
                                     candidate: FilePredictionCandidate,
                                     totalDuration: Long,
                                     refsComputation: Long,
                                     opened: Boolean) {
    FileNavigationLogger.logEvent(
      project, sessionId, candidate.features, candidate.path, prevPath, candidate.source, opened,
      totalDuration, candidate.featuresComputation, refsComputation, candidate.duration, candidate.probability
    )
  }
}

