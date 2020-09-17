package com.intellij.completion.ml.ranker.local

import com.intellij.openapi.application.PluginPathManager
import org.junit.Test
import java.io.File

class ModelMetadataReaderFromZipTest {
  @Test
  fun testReadSimpleZipModel() {
    val pathToZip = getTestDataPath("model.zip")
    val resourcesReader = ZipModelMetadataReader(pathToZip)
    val model = resourcesReader.readModel()
    assert(model.getSupportedLanguages().contains("python"))
    model.predict(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0))
  }

  private fun getTestDataPath(fileName: String): String {
    return File(PluginPathManager.getPluginHomePath("completion-ml-ranking") + "/testData/" + fileName).absolutePath
  }
}