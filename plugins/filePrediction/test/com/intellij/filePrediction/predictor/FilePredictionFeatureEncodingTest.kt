package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.features.FilePredictionFeature
import com.intellij.filePrediction.features.history.NextFileProbability
import com.intellij.filePrediction.predictor.FilePredictionFeatureEncoder.encodeBinary
import com.intellij.filePrediction.predictor.FilePredictionFeatureEncoder.encodeCategorical
import com.intellij.filePrediction.predictor.model.FilePredictionDecisionFunction
import com.intellij.filePrediction.predictor.model.FilePredictionModelProvider
import com.intellij.filePrediction.predictor.model.getFilePredictionModel
import com.intellij.filePrediction.predictor.model.setCustomTestFilePredictionModel
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.ResourcesModelMetadataReader
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture

class FilePredictionFeatureEncodingTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {
  private fun doTestBinary(features: Map<String, FilePredictionFeature>, expected: DoubleArray) {
    doTest(features, "binary", expected)
  }

  private fun doTestCategorical(features: Map<String, FilePredictionFeature>, expected: DoubleArray) {
    doTest(features, "categorical", expected)
  }

  private fun doTestNumerical(features: Map<String, FilePredictionFeature>, expected: DoubleArray) {
    doTest(features, "numerical", expected)
  }

  private fun doTest(features: Map<String, FilePredictionFeature>, metadataDir: String, expected: DoubleArray) {
    myFixture.addFileToProject("test.txt", "CURRENT FILE")

    val encoder = TestFilePredictionModelProvider(metadataDir)
    setCustomTestFilePredictionModel(testRootDisposable, encoder)

    val model = getFilePredictionModel()
    assertNotNull("Cannot find prediction model", model)

    model!!.predict(features)

    val actual = encoder.encoded
    assertNotNull(actual)

    assertEquals("Size of encoded features array is different from expected", expected.size, actual!!.size)
    for (i in actual.indices) {
      assertEquals("Encoded feature at position $i is different from expected", expected[i], actual[i])
    }
  }

  fun `test binary features encoding`() {
    val features = FilePredictionFeaturesBuilder()
      .withStructureFeatures(true, true, true, true).build()

    doTestBinary(features, encodeBinary(1.0, 1.0, 0.0, 1.0, 0.0))
  }

  fun `test binary features encoding with undefined`() {
    val features = FilePredictionFeaturesBuilder()
      .withInRef(true).build()

    doTestBinary(features, encodeBinary(0.0, 0.0, 1.0, 1.0, 1.0))
  }

  fun `test categorical features encoding`() {
    val features = FilePredictionFeaturesBuilder()
      .withFileType("JAVA")
      .withPrevFileType("Kotlin").build()

    doTestCategorical(features, encodeCategorical(1.0, 0.0, 0.0, 1.0))
  }

  fun `test categorical features encoding with unknown feature`() {
    val features = FilePredictionFeaturesBuilder()
      .withCustomFeature("unknown", "FEATURE_VALUE")
      .withFileType("Python")
      .withPrevFileType("Kotlin").build()

    doTestCategorical(features, encodeCategorical(0.0, 1.0, 0.0, 1.0))
  }

  fun `test categorical features encoding with undefined feature`() {
    val features = FilePredictionFeaturesBuilder()
      .withPrevFileType("Kotlin").build()

    doTestCategorical(features, encodeCategorical(0.0, 0.0, 0.0, 1.0))
  }

  fun `test numerical features encoding`() {
    val features = FilePredictionFeaturesBuilder()
      .withHistoryPosition(6)
      .withUniGram(NextFileProbability(0.4, 0.1, 0.9, 42.0, 0.0001)).build()

    doTestNumerical(features, encodeNumerical(6.0, 0.0, 0.4, 0.1, 0.9, 0.0))
  }

  fun `test numerical features with undefined position`() {
    val features = FilePredictionFeaturesBuilder()
      .withUniGram(NextFileProbability(0.4, 0.1, 0.9, 42.0, 0.0001)).build()

    doTestNumerical(features, encodeNumerical(99.0, 1.0, 0.4, 0.1, 0.9, 0.0))
  }

  fun `test numerical features with undefined uni gram`() {
    val features = FilePredictionFeaturesBuilder()
      .withHistoryPosition(6).build()

    doTestNumerical(features, encodeNumerical(6.0, 0.0, 0.0, 0.5, 0.0, 1.0))
  }
}

private object FilePredictionFeatureEncoder {
  fun encodeBinary(inProject: Double, inSource: Double, inSourceUndefined: Double, inLibrary: Double, inRef: Double): DoubleArray {
    val expected = DoubleArray(5)
    expected[0] = inProject
    expected[1] = inSourceUndefined
    expected[2] = inLibrary
    expected[3] = inSource
    expected[4] = inRef
    return expected
  }

  fun encodeCategorical(isFileTypeJava: Double,
                        isFileTypePython: Double,
                        isPrevFileTypeJava: Double,
                        isPrevFileTypeKotlin: Double): DoubleArray {
    val expected = DoubleArray(4)
    expected[0] = isFileTypeJava
    expected[1] = isPrevFileTypeKotlin
    expected[2] = isFileTypePython
    expected[3] = isPrevFileTypeJava
    return expected
  }

}

fun encodeNumerical(position: Double,
                    positionUndefined: Double,
                    uniMle: Double,
                    uniMin: Double,
                    uniMax: Double,
                    uniMaxUndefined: Double): DoubleArray {
  val expected = DoubleArray(6)
  expected[0] = position
  expected[1] = uniMax
  expected[2] = uniMle
  expected[3] = positionUndefined
  expected[4] = uniMin
  expected[5] = uniMaxUndefined
  return expected
}

private class TestFilePredictionModelProvider(val featuresDir: String) : FilePredictionModelProvider {
  private val baseDir: String = "com/intellij/filePrediction/predictor"
  var encoded: DoubleArray? = null

  override fun getModel(): DecisionFunction {
    val reader = ResourcesModelMetadataReader(FilePredictionFeatureEncodingTest::class.java, "$baseDir/$featuresDir")
    val metadata = FeaturesInfo.buildInfo(reader)
    return object : FilePredictionDecisionFunction(metadata) {
      override fun predict(features: DoubleArray?): Double {
        encoded = features
        return 0.5
      }
    }
  }
}