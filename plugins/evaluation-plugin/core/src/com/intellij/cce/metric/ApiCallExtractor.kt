package com.intellij.cce.metric

import com.intellij.cce.core.ExtractionOptions
import com.intellij.cce.core.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface ApiCallExtractor {
  suspend fun extractApiCalls(code: String, project: Project, extractionOptions: ExtractionOptions): List<String>
}

interface ApiCallExtractorProvider {
  val language: Language

  companion object {
    private val EP_NAME = ExtensionPointName<ApiCallExtractorProvider>("com.intellij.cce.apiCallExtractor")

    fun getForLanguage(language: Language): ApiCallExtractor? = EP_NAME.findFirstSafe { it.language == language }?.provide()
  }

  fun provide(): ApiCallExtractor
}