package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.FileReferencesComputationResult
import com.intellij.filePrediction.candidates.CompositeCandidateProvider
import com.intellij.filePrediction.features.FilePredictionFeaturesHelper
import com.intellij.filePrediction.predictor.model.FilePredictionModel
import com.intellij.filePrediction.predictor.model.getFilePredictionModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class FileUsagePredictor(val isDummy: Boolean) {
  internal abstract fun predictNextFile(
    project: Project, currentFile: VirtualFile?,
    refs: FileReferencesComputationResult, topCandidates: Int
  ): List<FilePredictionCandidate>
}

private class FileUsageSimplePredictor : FileUsagePredictor(true) {
  override fun predictNextFile(project: Project,
                               currentFile: VirtualFile?,
                               refs: FileReferencesComputationResult,
                               topCandidates: Int): List<FilePredictionCandidate> {
    val references = refs.value.references
    val candidateFiles = CompositeCandidateProvider.provideCandidates(project, currentFile, references, topCandidates)

    val candidates: MutableList<FilePredictionCandidate> = arrayListOf()
    for (candidate in candidateFiles) {
      val features = FilePredictionFeaturesHelper.calculateFileFeatures(project, candidate, refs.value, currentFile)
      candidates.add(FilePredictionCandidate(features, candidate.path))
    }
    return candidates
  }
}

private class FileUsageMLPredictor(private val model: FilePredictionModel) : FileUsagePredictor(false) {
  override fun predictNextFile(project: Project,
                               currentFile: VirtualFile?,
                               refs: FileReferencesComputationResult,
                               topCandidates: Int): List<FilePredictionCandidate> {
    val candidates: MutableList<FilePredictionCandidate> = arrayListOf()
    val references = refs.value.references
    val candidateFiles = CompositeCandidateProvider.provideCandidates(project, currentFile, references, topCandidates)
    for (candidate in candidateFiles) {
      val features = FilePredictionFeaturesHelper.calculateFileFeatures(project, candidate, refs.value, currentFile)
      val probability = model.predict(features.value)
      candidates.add(FilePredictionCandidate(features, candidate.path, probability))
    }
    candidates.sortByDescending { it.probability }
    return candidates
  }
}

object FileUsagePredictorProvider {
  fun getFileUsagePredictor(): FileUsagePredictor {
    val model = getFilePredictionModel()
    if (model != null) {
      return FileUsageMLPredictor(model)
    }
    return FileUsageSimplePredictor()
  }
}