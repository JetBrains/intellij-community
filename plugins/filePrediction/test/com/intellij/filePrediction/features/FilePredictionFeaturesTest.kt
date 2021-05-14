package com.intellij.filePrediction.features

import com.intellij.filePrediction.FileReferencesComputationResult
import com.intellij.filePrediction.candidates.FilePredictionCandidateSource.OPEN
import com.intellij.filePrediction.features.history.ngram.FilePredictionNGramFeatures
import com.intellij.filePrediction.predictor.FilePredictionCandidate
import com.intellij.filePrediction.predictor.FilePredictionCompressedCandidatesHolder
import com.intellij.filePrediction.references.FilePredictionReferencesHelper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import junit.framework.TestCase
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

/**
 * Smoke tests for a composite features provider, for provider specific checks use dedicated test class
 */
class FilePredictionFeaturesTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

  private fun doTestFeatures(vararg expectedFeatures: String) {
    val prevFile = myFixture.addFileToProject("prevFile.txt", "PREVIOUS FILE").virtualFile
    val candidate = myFixture.addFileToProject("candidate.txt", "CANDIDATE").virtualFile

    val references: FileReferencesComputationResult = ApplicationManager.getApplication().executeOnPooledThread(Callable {
      FilePredictionReferencesHelper.calculateExternalReferences(myFixture.project, prevFile)
    }).get(1, TimeUnit.SECONDS)
    val result = FilePredictionFeaturesCache(references.value, FilePredictionNGramFeatures(emptyMap()))
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

    val references: FileReferencesComputationResult = ApplicationManager.getApplication().executeOnPooledThread(Callable {
      FilePredictionReferencesHelper.calculateExternalReferences(myFixture.project, prevFile)
    }).get(1, TimeUnit.SECONDS)

    val result = FilePredictionFeaturesCache(references.value, FilePredictionNGramFeatures(emptyMap()))
    val features = FilePredictionFeaturesHelper.calculateFileFeatures(myFixture.project, candidateFile, result, prevFile)
    assertNotEmpty(features.value.keys)

    val before = FilePredictionCandidate(candidateFile.path, OPEN, features.value, 5, 10, 0.1)
    val beforeFeaturesSize = groupFeaturesByProviders(before).values

    val holder = FilePredictionCompressedCandidatesHolder.create(listOf(before))
    val afterCandidates = holder.getCandidates()
    assertTrue(afterCandidates.size == 1)

    val after = afterCandidates[0]
    val parsedFeatures = after.features.split(';')
      .map { byProvider ->
        byProvider.split(',').map { feature -> if (feature.isNotEmpty()) feature else null }
      }
    TestCase.assertEquals(before.features.size, parsedFeatures.flatten().filterNotNull().size)

    val afterFeaturesSize = parsedFeatures.mapNotNull {
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

  private fun doTestFeaturesEncoding(features: Map<String, FilePredictionFeature>, codes: List<List<String>>, expected: String) {
    val actual = FilePredictionCompressedCandidatesHolder.encodeFeatures(features, codes)
    TestCase.assertEquals("Features are not encoded correctly", expected, actual)
  }

  fun `test composite feature provider is not empty`() {
    doTestFeatures(
      "core_file_type",
      "core_prev_file_type",
      "similarity_in_source",
      "similarity_in_project",
      "similarity_in_library",
      "similarity_excluded",
      "similarity_same_dir",
      "similarity_same_module",
      "similarity_name_prefix",
      "similarity_path_prefix",
      "similarity_relative_path_prefix",
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

  fun `test boolean features are encoded correctly`() {
    val features = hashMapOf(
      "true_bool" to FilePredictionFeature.binary(true),
      "false_bool" to FilePredictionFeature.binary(false)
    )
    val codes: List<List<String>> = arrayListOf(
      arrayListOf("true_bool", "false_bool")
    )
    doTestFeaturesEncoding(features, codes, "1,0")
  }

  fun `test numerical features are encoded correctly`() {
    val features = hashMapOf(
      "positive_int" to FilePredictionFeature.numerical(125),
      "negative_int" to FilePredictionFeature.numerical(-977),
      "zero" to FilePredictionFeature.numerical(0),
      "positive_double" to FilePredictionFeature.numerical(123.521),
      "negative_double" to FilePredictionFeature.numerical(-12.123),
      "zero_double" to FilePredictionFeature.numerical(0.0),
      "nan" to FilePredictionFeature.numerical(Double.NaN),
      "positive_inf" to FilePredictionFeature.numerical(Double.POSITIVE_INFINITY),
      "negative_inf" to FilePredictionFeature.numerical(Double.NEGATIVE_INFINITY),
      "small_double" to FilePredictionFeature.numerical(0.0000000001)
    )
    val codes: List<List<String>> = arrayListOf(arrayListOf(
      "positive_int", "negative_int", "zero",
      "positive_double", "negative_double", "zero_double",
      "nan", "positive_inf", "negative_inf", "small_double"
    ))
    doTestFeaturesEncoding(features, codes, "125,-977,0,123.521,-12.123,0.0,-1.0,-1.0,-1.0,0.0")
  }

  fun `test file type features are encoded correctly`() {
    val features = hashMapOf(
      "file_type" to FilePredictionFeature.fileType("JAVA"),
      "custom_file_type" to FilePredictionFeature.fileType("CUSTOM_FILE_TYPE"),
      "unknown_file_type" to FilePredictionFeature.fileType("UNKNOWN")
    )
    val codes: List<List<String>> = arrayListOf(
      arrayListOf("file_type", "custom_file_type", "unknown_file_type")
    )
    doTestFeaturesEncoding(features, codes, "JAVA,UNKNOWN,UNKNOWN")
  }

  fun `test missing features are encoded correctly`() {
    val features = hashMapOf(
      "bool" to FilePredictionFeature.binary(true),
      "positive_int" to FilePredictionFeature.numerical(123),
      "negative_int" to FilePredictionFeature.numerical(-2),
      "nan" to FilePredictionFeature.numerical(Double.NaN),
      "double" to FilePredictionFeature.numerical(0.001)
    )
    val codes: List<List<String>> = arrayListOf(
      arrayListOf("negative_double", "double", "bool", "positive_double", "nan", "positive_int")
    )
    doTestFeaturesEncoding(features, codes, ",0.001,1,,-1.0,123")
  }

  fun `test features from different providers are encoded correctly`() {
    val features = hashMapOf(
      "bool" to FilePredictionFeature.binary(true),
      "positive_int" to FilePredictionFeature.numerical(123),
      "negative_int" to FilePredictionFeature.numerical(-2),
      "nan" to FilePredictionFeature.numerical(Double.NaN),
      "double" to FilePredictionFeature.numerical(0.001)
    )
    val codes: List<List<String>> = arrayListOf(
      arrayListOf("double", "nan"),
      arrayListOf("bool"),
      arrayListOf("positive_int", "negative_int")
    )
    doTestFeaturesEncoding(features, codes, "0.001,-1.0;1;123,-2")
  }

  fun `test missing features from different providers are encoded correctly`() {
    val features = hashMapOf(
      "bool" to FilePredictionFeature.binary(true),
      "positive_int" to FilePredictionFeature.numerical(123),
      "negative_int" to FilePredictionFeature.numerical(-2),
      "nan" to FilePredictionFeature.numerical(Double.NaN),
      "double" to FilePredictionFeature.numerical(0.001)
    )
    val codes: List<List<String>> = arrayListOf(
      arrayListOf("negative_double", "double", "positive_double", "nan"),
      arrayListOf("bool"),
      arrayListOf("positive_int", "int")
    )
    doTestFeaturesEncoding(features, codes, ",0.001,,-1.0;1;123,")
  }

  fun `test missing all features from provider are encoded correctly`() {
    val features = hashMapOf(
      "bool" to FilePredictionFeature.binary(true),
      "positive_int" to FilePredictionFeature.numerical(123),
      "negative_int" to FilePredictionFeature.numerical(-2),
      "double" to FilePredictionFeature.numerical(0.001)
    )
    val codes: List<List<String>> = arrayListOf(
      arrayListOf("nan"),
      arrayListOf("double", "bool"),
      arrayListOf("file_type", "custom_file_type"),
      arrayListOf("positive_int", "negative_int")
    )
    doTestFeaturesEncoding(features, codes, ";0.001,1;,;123,-2")
  }
}