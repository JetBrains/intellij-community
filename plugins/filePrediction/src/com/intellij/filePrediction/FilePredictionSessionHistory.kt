// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.candidates.FilePredictionCandidateSource
import com.intellij.filePrediction.predictor.FilePredictionCompressedCandidate
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly

@Service(Service.Level.PROJECT)
internal class FilePredictionSessionHistory {
  companion object {
    private const val DEFAULT_CANDIDATES_PER_SESSION = 25

    fun getInstance(project: Project) = project.service<FilePredictionSessionHistory>()
  }

  private var candidatesPerSession: Int = DEFAULT_CANDIDATES_PER_SESSION

  private var previousCandidates: Array<Set<String>> = Array(3) { emptySet() }

  @TestOnly
  fun setCandidatesPerSession(size: Int) {
    candidatesPerSession = size
  }

  fun onCandidatesCalculated(candidates: List<FilePredictionCompressedCandidate>) {
    shiftSessionsHistory()

    previousCandidates[0] = filterCandidates(candidates)
  }

  private fun shiftSessionsHistory() {
    for (i in previousCandidates.size - 1 downTo 1) {
      previousCandidates[i] = previousCandidates[i - 1]
    }
  }

  private fun filterCandidates(candidates: List<FilePredictionCompressedCandidate>): Set<String> {
    if (candidatesPerSession == 0) {
      return emptySet()
    }
    return candidates
      .filter { it.source == FilePredictionCandidateSource.NEIGHBOR || it.source == FilePredictionCandidateSource.REFERENCE }
      .take(candidatesPerSession).map { it.path }.toSet()
  }

  fun selectCandidates(limitPerSession: Int): Set<String> {
    if (!hasSessionsHistory()) {
      return emptySet()
    }

    val result: HashSet<String> = hashSetOf()
    for (candidates in previousCandidates) {
      result.addAll(candidates.take(limitPerSession))
    }
    return result
  }

  private fun hasSessionsHistory(): Boolean {
    return previousCandidates.any { it.isNotEmpty() }
  }
}