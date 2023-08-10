package com.intellij.completion.ml.ranker.local

import com.intellij.internal.ml.DecisionFunction
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.util.zip.ZipFile

data class DecisionFunctionWithLanguages(val decisionFunction: DecisionFunction, val languages: List<String>)

@ApiStatus.Internal
interface LocalZipModelProvider {
  companion object {
    private val EP_NAME = ExtensionPointName.create<LocalZipModelProvider>("com.intellij.completion.ml.localModelProvider")

    fun findModelProvider(zipFile: ZipFile): LocalZipModelProvider? {
      return EP_NAME.extensionList.singleOrNull { it.isSupportedFormat(zipFile) }
    }
  }

  fun isSupportedFormat(file: ZipFile): Boolean
  fun loadModel(file: ZipFile): DecisionFunctionWithLanguages
}