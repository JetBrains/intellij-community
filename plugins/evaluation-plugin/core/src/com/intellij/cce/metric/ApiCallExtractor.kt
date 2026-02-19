package com.intellij.cce.metric

import com.intellij.cce.core.Language
import com.intellij.cce.core.TokenProperties
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface ApiCallExtractor {

  companion object {
    val logger: Logger = Logger.getInstance(ApiCallExtractor::class.java)
  }

  suspend fun extractApiCalls(
    code: String,
    allCodeSnippets: List<String>,
    project: Project,
    tokenProperties: TokenProperties,
  ): List<String>

  suspend fun extractExternalApiCalls(
    code: String,
    allCodeSnippets: List<String>,
    project: Project,
    tokenProperties: TokenProperties,
  ): List<String> {
    throw NotImplementedError("External API calls extraction is not implemented")
  }
}

interface ApiCallExtractorProvider {
  val language: Language

  companion object {
    private val EP_NAME = ExtensionPointName<ApiCallExtractorProvider>("com.intellij.cce.apiCallExtractor")

    fun getForLanguage(language: Language): ApiCallExtractor? = EP_NAME.findFirstSafe { it.language == language }?.provide()
  }

  fun provide(): ApiCallExtractor
}