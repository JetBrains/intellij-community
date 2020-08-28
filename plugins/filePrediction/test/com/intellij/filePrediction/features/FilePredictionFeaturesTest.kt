package com.intellij.filePrediction.features

import com.intellij.filePrediction.candidates.FilePredictionCandidateSource
import com.intellij.filePrediction.candidates.FilePredictionCandidateSource.OPEN
import com.intellij.filePrediction.predictor.FilePredictionCandidate
import com.intellij.filePrediction.predictor.FilePredictionCompressedCandidatesHolder
import com.intellij.filePrediction.references.FilePredictionReferencesHelper
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import junit.framework.TestCase
import org.junit.Test

/**
 * Smoke tests for a composite features provider, for provider specific checks use dedicated test class
 */
class FilePredictionFeaturesTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

  private fun doTestFeatures(vararg expectedFeatures: String) {
    val prevFile = myFixture.addFileToProject("prevFile.txt", "PREVIOUS FILE").virtualFile
    val candidate = myFixture.addFileToProject("candidate.txt", "CANDIDATE").virtualFile

    val result = FilePredictionReferencesHelper.calculateExternalReferences(myFixture.project, prevFile).value
    val actual = FilePredictionFeaturesHelper.calculateFileFeatures(myFixture.project, candidate, result, prevFile)
    assertNotEmpty(actual.value.keys)

    val features = actual.value.keys
    for (expected in expectedFeatures) {
      assertTrue(features.contains(expected))
    }
  }

  private fun doTestCandidatesEncoding() {
    val prevFile = myFixture.addFileToProject("prevFile.txt", "PREVIOUS FILE").virtualFile
    val candidateFile = myFixture.addFileToProject("candidate.txt", "CANDIDATE").virtualFile


    val result = FilePredictionReferencesHelper.calculateExternalReferences(myFixture.project, prevFile).value
    val features = FilePredictionFeaturesHelper.calculateFileFeatures(myFixture.project, candidateFile, result, prevFile)
    assertNotEmpty(features.value.keys)

    val before = FilePredictionCandidate(candidateFile.path, OPEN, features.value, 5, 10, 0.1)
    val holder = FilePredictionCompressedCandidatesHolder.create(listOf(before))
    val after = holder.getCandidates()
    assertTrue(after.size == 1)
    TestCase.assertEquals(before, after[0])
  }

  @Test
  fun `test composite feature provider is not empty`() {
    doTestFeatures(
      "core_file_type",
      "core_prev_file_type",
      "core_in_source",
      "core_in_project",
      "core_in_library",
      "core_excluded",
      "core_same_dir",
      "core_same_module",
      "core_name_prefix",
      "core_path_prefix",
      "core_relative_path_prefix",
      "history_size",
      "history_uni_mle",
      "history_bi_mle"
    )
  }

  fun `test features do not change after encoding and decoding`() {
    doTestCandidatesEncoding()
  }

  fun `test features are ordered alphabetically`() {
    val providers = FilePredictionFeaturesHelper.EP_NAME.extensionList
    for (provider in providers) {
      val features = provider.getFeatures()
      val sortedFeatures = features.sorted()
      for ((index, feature) in features.withIndex()) {
        TestCase.assertEquals("Features should be sorted alphabetically", sortedFeatures[index], feature)
      }
    }
  }
}