// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.CompositeCandidateProvider.provideCandidates
import com.intellij.filePrediction.FileFeaturesComputationResult
import com.intellij.filePrediction.FileNavigationLogger
import com.intellij.filePrediction.FilePredictionFeaturesHelper
import com.intellij.filePrediction.FileReferencesComputationResult
import com.intellij.filePrediction.predictor.model.FilePredictionModel
import com.intellij.filePrediction.predictor.model.getFilePredictionModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class FileUsagePredictor(private val candidatesLimit: Int,
                                  private val logTopLimit: Int,
                                  private val logTotalLimit: Int) {
  fun predictNextFile(project: Project, file: VirtualFile) {
    val result = FilePredictionFeaturesHelper.calculateExternalReferences(project, file)

    val model = getFilePredictionModel()
    if (model != null) {
      predictAndLogCandidates(project, model, file, result)
    }
    else {
      logCandidates(project, file, result)
    }
  }

  private fun logCandidates(project: Project, file: VirtualFile, refs: FileReferencesComputationResult) {
    val candidateFiles = provideCandidates(project, file, refs.value.references, logTotalLimit)
    for (candidate in candidateFiles) {
      val features = FilePredictionFeaturesHelper.calculateFileFeatures(project, candidate, refs.value, file)
      FileNavigationLogger.logEvent(project, "candidate.calculated", features, candidate.path, refs.duration)
    }
  }

  private fun predictAndLogCandidates(project: Project,
                                      model: FilePredictionModel,
                                      file: VirtualFile,
                                      refs: FileReferencesComputationResult) {
    val candidates: MutableList<FilePredictionCandidate> = arrayListOf()
    val candidateFiles = provideCandidates(project, file, refs.value.references, candidatesLimit)
    for (candidate in candidateFiles) {
      val features = FilePredictionFeaturesHelper.calculateFileFeatures(project, candidate, refs.value, file)
      val probability = model.predict(features.value)
      candidates.add(FilePredictionCandidate(features, candidate.path, probability))
    }

    candidates.sortByDescending { it.probability }
    logCandidatesWithProbability(project, candidates, refs.duration)
  }

  private fun logCandidatesWithProbability(project: Project, candidates: MutableList<FilePredictionCandidate>, refsComputation: Long) {
    val head = candidates.take(logTopLimit)
    logCalculatedCandidates(project, head, refsComputation)

    if (candidates.size > logTopLimit) {
      val tail = candidates.subList(logTopLimit, candidates.size)
      val randomToLog = tail.shuffled().take(logTotalLimit - logTopLimit)
      logCalculatedCandidates(project, randomToLog, refsComputation)
    }
  }

  private fun logCalculatedCandidates(project: Project, candidates: Collection<FilePredictionCandidate>, refsComputation: Long) {
    for (candidate in candidates) {
      val probability = candidate.probability
      val features = candidate.features
      FileNavigationLogger.logEvent(project, "candidate.calculated", features, candidate.path, refsComputation, probability)
    }
  }
}

private class FilePredictionCandidate(val features: FileFeaturesComputationResult, val path: String, val probability: Double)
