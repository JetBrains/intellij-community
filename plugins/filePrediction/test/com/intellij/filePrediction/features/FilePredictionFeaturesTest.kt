package com.intellij.filePrediction.features

import com.intellij.filePrediction.FilePredictionFeaturesHelper
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import org.junit.Test

/**
 * Smoke tests for a composite features provider, for provider specific checks use dedicated test class
 */
class FilePredictionFeaturesTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

  private fun doTestFeatures(vararg expectedFeatures: String) {
    val prevFile = myFixture.addFileToProject("prevFile.txt", "PREVIOUS FILE").virtualFile
    val candidate = myFixture.addFileToProject("candidate.txt", "CANDIDATE").virtualFile

    val result = FilePredictionFeaturesHelper.calculateExternalReferences(myFixture.project, prevFile).value
    val actual = FilePredictionFeaturesHelper.calculateFileFeatures(myFixture.project, candidate, result, prevFile)
    assertNotEmpty(actual.value.keys)

    val features = actual.value.keys
    for (expected in expectedFeatures) {
      assertTrue(features.contains(expected))
    }
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
}