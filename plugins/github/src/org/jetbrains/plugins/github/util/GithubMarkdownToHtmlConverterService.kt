// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.collaboration.markdown.ConverterProvidersConfig
import com.intellij.collaboration.markdown.MarkdownToHtmlConverter
import com.intellij.collaboration.markdown.ReviewFlavourDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe

@Service(Level.PROJECT)
class GithubMarkdownToHtmlConverterService {
  fun convertToHtml(project: Project?, markdownText: String, server: String? = null): String {
    return MarkdownToHtmlConverter(project) { converterConfig: ConverterProvidersConfig ->
      ReviewFlavourDescriptor(converterConfig)
    }.convertToHtml(markdownText, server)
  }

  companion object {
    fun getInstance(): GithubMarkdownToHtmlConverterService = service()
  }
}

@NlsSafe
internal fun String.convertToHtml(): String {
  return GithubMarkdownToHtmlConverterService.getInstance().convertToHtml(project = null, markdownText = this)
}