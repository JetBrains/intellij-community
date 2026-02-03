// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.logger

import com.intellij.filePrediction.candidates.FilePredictionCandidateSource.OPEN
import com.intellij.filePrediction.candidates.FilePredictionCandidateSource.RECENT
import com.intellij.filePrediction.predictor.FilePredictionCompressedCandidate
import com.intellij.openapi.project.Project

internal class FileUsagePredictionLogger(private val logTopLimit: Int, private val logTotalLimit: Int) {

  fun logCandidates(project: Project, sessionId: Int,
                    opened: FilePredictionCompressedCandidate?,
                    candidates: List<FilePredictionCompressedCandidate>,
                    total: Long, refs: Long) {
    val toLog = selectCandidatesToLog(candidates)
    FileNavigationLogger.logEvent(project, sessionId, opened, toLog, total, refs)
  }

  private fun selectCandidatesToLog(candidates: List<FilePredictionCompressedCandidate>): List<FilePredictionCompressedCandidate> {
    val head = candidates.take(logTopLimit)
    if (candidates.size <= logTopLimit) {
      return head
    }

    val numOfRecentCandidates = head.filter { isRecentCandidate(it) }.count()
    val toLog: MutableList<FilePredictionCompressedCandidate> = arrayListOf()
    toLog.addAll(head)

    var tail = candidates.subList(logTopLimit, candidates.size)
    if (numOfRecentCandidates > logTopLimit / 2) {
      tail = tail.filter { !isRecentCandidate(it) }
    }

    val randomToLog = tail.shuffled().take(logTotalLimit - logTopLimit)
    toLog.addAll(randomToLog)
    return toLog
  }

  private fun isRecentCandidate(candidate: FilePredictionCompressedCandidate) = candidate.source == OPEN || candidate.source == RECENT
}

