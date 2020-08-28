package com.intellij.filePrediction.features

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
    val beforeFeaturesSize = groupFeaturesByProviders(before).values

    val holder = FilePredictionCompressedCandidatesHolder.create(listOf(before))
    val afterCandidates = holder.getCandidates()
    assertTrue(afterCandidates.size == 1)

    val after = afterCandidates[0]
    TestCase.assertEquals(before.features.size, after.features.flatten().filterNotNull().size)

    val afterFeaturesSize = after.features.mapNotNull {
      val filtered = it.filterNotNull()
      if (filtered.isNotEmpty()) filtered.size else null
    }
    TestCase.assertEquals("Number of providers is different after encoding", beforeFeaturesSize.size, afterFeaturesSize.size)

    val diff: MutableList<Int> = arrayListOf(*beforeFeaturesSize.toTypedArray())
    diff.removeAll(afterFeaturesSize)
    TestCase.assertTrue("Number of features in providers is different after encoding", diff.isEmpty())
  }

  private fun groupFeaturesByProviders(before: FilePredictionCandidate): Map<String, Int> {
    val result: HashMap<String, Int> = hashMapOf()
    for (feature in before.features) {
      val provider = feature.key.subSequence(0, feature.key.indexOf('_')).toString()
      result[provider] = result.getOrDefault(provider, 0) + 1
    }
    return result
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

  fun `test number of features do not change after encoding and decoding`() {
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

  fun `test providers are ordered alphabetically`() {
    val featuresByProviders = FilePredictionFeaturesHelper.getFeaturesByProviders()
    val features = featuresByProviders.flatten()
    val sortedFeatures = features.sorted()
    for ((index, feature) in features.withIndex()) {
      TestCase.assertEquals("Features should be sorted alphabetically", sortedFeatures[index], feature)
    }
  }
}