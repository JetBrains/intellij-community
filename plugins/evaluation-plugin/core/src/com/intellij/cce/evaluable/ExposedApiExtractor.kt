package com.intellij.cce.evaluable

import com.intellij.cce.core.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile

interface ExposedApiExtractor {
  companion object {
    val EP_NAME: ExtensionPointName<ExposedApiExtractor> = ExtensionPointName.create("com.intellij.cce.exposedApiExtractor")
    fun getForLanguage(language: Language): ExposedApiExtractor? = EP_NAME.findFirstSafe { it.language == language }
  }

  val language: Language

  suspend fun extractExposedApi(psiFile: PsiFile): List<String>
}