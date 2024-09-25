package com.intellij.cce.metric

import com.intellij.cce.core.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface ApiCallExtractor {

  companion object {
    val EP_NAME: ExtensionPointName<ApiCallExtractor> = ExtensionPointName.create("com.intellij.cce.apiCallExtractor")
    fun getForLanguage(language: Language): ApiCallExtractor? = EP_NAME.findFirstSafe { it.language == language }
  }

  val language: Language
  suspend fun extractApiCalls(code: String, project: Project): List<String>
}