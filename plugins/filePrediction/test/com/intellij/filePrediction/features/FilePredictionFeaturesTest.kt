package com.intellij.filePrediction.features

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

    val before = FilePredictionCandidate(candidateFile.path, "manual", features.value, 5, 10, 0.1)
    val holder = FilePredictionCompressedCandidatesHolder.create(listOf(before))
    val after = holder.getCandidates()
    assertTrue(after.size == 1)
    TestCase.assertEquals(before, after[0])
  }

  @Test
  fun `test composite feature provider is not empty`() {
    doTestFeatures(
      "file_type",
      "prev_file_type",
      "in_source",
      "in_project",
      "in_library",
      "excluded",
      "same_dir",
      "same_module",
      "name_prefix",
      "path_prefix",
      "relative_path_prefix",
      "history_size",
      "history_uni_mle",
      "history_bi_mle"
    )
  }

  fun `test features do not change after encoding and decoding`() {
    doTestCandidatesEncoding()
  }
}