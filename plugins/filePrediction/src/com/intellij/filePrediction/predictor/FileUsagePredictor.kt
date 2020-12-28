// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.FileReferencesComputationResult
import com.intellij.filePrediction.candidates.CompositeCandidateProvider
import com.intellij.filePrediction.candidates.FilePredictionCandidateFile
import com.intellij.filePrediction.candidates.FilePredictionCandidateProvider
import com.intellij.filePrediction.features.FilePredictionFeaturesCache
import com.intellij.filePrediction.features.FilePredictionFeaturesHelper
import com.intellij.filePrediction.features.history.FilePredictionHistory
import com.intellij.filePrediction.predictor.model.FilePredictionModel
import com.intellij.filePrediction.predictor.model.getFilePredictionModel
import com.intellij.filePrediction.references.ExternalReferencesResult
import com.intellij.filePrediction.references.FilePredictionReferencesHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class FileUsagePredictor(val candidateProvider: FilePredictionCandidateProvider, val isDummy: Boolean) {

  fun calculateFeaturesCache(project: Project,
                             candidates: Collection<FilePredictionCandidateFile>,
                             refs: ExternalReferencesResult): FilePredictionFeaturesCache {
    val nGrams = FilePredictionHistory.getInstance(project).batchCalculateNGrams(candidates.map { it.file.url })
    return FilePredictionFeaturesCache(refs, nGrams)
  }

  fun predictNextFile(project: Project, currentFile: VirtualFile?, topCandidates: Int): List<FilePredictionCandidate> {
    val refs = FilePredictionReferencesHelper.calculateExternalReferences(project, currentFile)
    return predictNextFile(project, currentFile, refs, topCandidates)
  }

  internal abstract fun predictNextFile(
    project: Project, currentFile: VirtualFile?,
    refs: FileReferencesComputationResult, topCandidates: Int
  ): List<FilePredictionCandidate>
}

private class FileUsageSimplePredictor(candidateProvider: FilePredictionCandidateProvider) : FileUsagePredictor(candidateProvider, true) {
  override fun predictNextFile(project: Project,
                               currentFile: VirtualFile?,
                               refs: FileReferencesComputationResult,
                               topCandidates: Int): List<FilePredictionCandidate> {
    val references = refs.value.references
    val candidateFiles = candidateProvider.provideCandidates(project, currentFile, references, topCandidates)
    val cache = calculateFeaturesCache(project, candidateFiles, refs.value)
    val candidates: MutableList<FilePredictionCandidate> = arrayListOf()
    for (candidate in candidateFiles) {
      val features =
        FilePredictionFeaturesHelper.calculateFileFeatures(project, candidate.file, cache, currentFile)
      candidates.add(FilePredictionCandidate(candidate.file.path, candidate.source, features.value, features.duration))
    }
    return candidates
  }
}

private class FileUsageMLPredictor(candidateProvider: FilePredictionCandidateProvider, private val model: FilePredictionModel) : FileUsagePredictor(candidateProvider, false) {
  override fun predictNextFile(project: Project,
                               currentFile: VirtualFile?,
                               refs: FileReferencesComputationResult,
                               topCandidates: Int): List<FilePredictionCandidate> {
    val candidates: MutableList<FilePredictionCandidate> = arrayListOf()
    val references = refs.value.references
    val candidateFiles = candidateProvider.provideCandidates(project, currentFile, references, topCandidates)
    val cache = calculateFeaturesCache(project, candidateFiles, refs.value)
    for (candidate in candidateFiles) {
      val features = FilePredictionFeaturesHelper.calculateFileFeatures(project, candidate.file, cache, currentFile)
      val start = System.currentTimeMillis()
      val probability = model.predict(features.value)
      val duration = System.currentTimeMillis() - start
      candidates.add(FilePredictionCandidate(candidate.file.path, candidate.source, features.value, features.duration, duration, probability))
    }
    candidates.sortByDescending { it.probability }
    return candidates
  }
}

object FileUsagePredictorProvider {
  fun getFileUsagePredictor(customCandidateProvider: FilePredictionCandidateProvider? = null): FileUsagePredictor {
    val provider: FilePredictionCandidateProvider = customCandidateProvider ?: CompositeCandidateProvider()
    val model = getFilePredictionModel()
    if (model != null) {
      return FileUsageMLPredictor(provider, model)
    }
    return FileUsageSimplePredictor(provider)
  }
}