// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.logger.FileUsagePredictionLogger
import com.intellij.filePrediction.predictor.*
import com.intellij.filePrediction.predictor.FilePredictionCompressedCandidatesHolder
import com.intellij.filePrediction.references.FilePredictionReferencesHelper
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.atomic.AtomicInteger

private val counter = AtomicInteger(0)

internal class FilePredictionSession(predict: Boolean, threshold: Double) {
  val id: Int = counter.incrementAndGet()
  val shouldLog: Boolean = predict && Math.random() < threshold

  var candidatesHolder: FilePredictionCandidatesHolder? = null
  var totalDuration: Long = -1
  var refsDuration: Long = -1

  fun candidatesCalculated(holder: FilePredictionCandidatesHolder, total: Long, refs: Long) {
    candidatesHolder = holder
    totalDuration = total
    refsDuration = refs
  }

  fun findOpenedCandidate(file: VirtualFile, candidates: List<FilePredictionCompressedCandidate>): FilePredictionCompressedCandidate? {
    for (candidate in candidates) {
      if (StringUtil.equals(candidate.path, file.path)) {
        return candidate
      }
    }
    return null
  }
}

internal class FilePredictionSessionManager(private val candidatesLimit: Int,
                                            logTopLimit: Int,
                                            private val logTotalLimit: Int,
                                            private val threshold: Double) {
  private val predictor: FileUsagePredictor = FileUsagePredictorProvider.getFileUsagePredictor()
  private val logger: FileUsagePredictionLogger = FileUsagePredictionLogger(logTopLimit, logTotalLimit)

  private var session: FilePredictionSession? = null

  @Synchronized
  fun onSessionStarted(project: Project, file: VirtualFile) {
    session?.let {
      if (it.shouldLog) {
        logSessionFinished(project, it, file)
      }
    }

    startSession(project, file)
  }

  private fun startSession(project: Project, file: VirtualFile) {
    val newSession = FilePredictionSession(!PowerSaveMode.isEnabled(), threshold)
    if (newSession.shouldLog) {
      val start = System.currentTimeMillis()
      val refs = FilePredictionReferencesHelper.calculateExternalReferences(project, file)
      val candidatesToCalculate = if (!predictor.isDummy) candidatesLimit else logTotalLimit
      val candidates = predictor.predictNextFile(project, file, refs, candidatesToCalculate)
      val holder = FilePredictionCompressedCandidatesHolder.create(candidates)
      val totalDuration = System.currentTimeMillis() - start
      newSession.candidatesCalculated(holder, totalDuration, refs.duration)
    }
    session = newSession
  }

  private fun logSessionFinished(project: Project, currentSession: FilePredictionSession, openedFile: VirtualFile) {
    val candidates = currentSession.candidatesHolder?.getCandidates() ?: emptyList()
    if (candidates.isNotEmpty()) {
      val sessionId = currentSession.id
      val refsDuration = currentSession.refsDuration
      val totalDuration = currentSession.totalDuration

      val opened = currentSession.findOpenedCandidate(openedFile, candidates)
      val notOpened = candidates.filter { it != opened }
      FilePredictionSessionHistory.getInstance(project).onCandidatesCalculated(notOpened)

      logger.logCandidates(project, sessionId, opened, notOpened, totalDuration, refsDuration)
    }
  }
}