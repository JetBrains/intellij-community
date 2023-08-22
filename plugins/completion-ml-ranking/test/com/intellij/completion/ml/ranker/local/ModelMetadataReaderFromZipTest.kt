package com.intellij.completion.ml.ranker.local

import com.intellij.completion.ml.ranker.local.catboost.LocalCatBoostModelProvider
import com.intellij.completion.ml.ranker.local.randomforest.LocalRandomForestProvider
import com.intellij.openapi.application.PluginPathManager
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.zip.ZipFile

@RunWith(value = Parameterized::class)
class ModelMetadataReaderFromZipTest {
  companion object {

    @JvmStatic
    @Parameterized.Parameters(name = "{index}: provider:{0}, fileName:{1}, language:{2}, x:{3}")
    fun getTestParameters(): Array<Array<Any>> {
      return arrayOf(
        arrayOf(LocalRandomForestProvider(), "model.zip", "python", doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)),
        arrayOf(LocalCatBoostModelProvider(), "catboost_model.zip", "java", doubleArrayOf(1.0, .0, .0, 1.0, .0, .0, .0))
      )
    }
  }

  @Parameterized.Parameter(0)
  lateinit var provider: LocalZipModelProvider

  @Parameterized.Parameter(1)
  lateinit var fileName: String

  @Parameterized.Parameter(2)
  lateinit var language: String

  @Parameterized.Parameter(3)
  lateinit var features: DoubleArray

  @Test
  fun testReadRandomForestZipModel() {
    val pathToZip = getTestDataPath(fileName)
    ZipFile(pathToZip).use { file ->
      assert(provider.isSupportedFormat(file))
      val (model, languages) = provider.loadModel(file)
      assert(languages.contains(language))
      model.predict(features)
    }
  }

  private fun getTestDataPath(fileName: String): String {
    return File(PluginPathManager.getPluginHomePath("completion-ml-ranking") + "/testData/" + fileName).absolutePath
  }
}