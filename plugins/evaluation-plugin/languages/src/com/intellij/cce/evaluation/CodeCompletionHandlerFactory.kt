// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.core.Language
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface CodeCompletionHandlerFactory {
  fun isApplicable(language: Language): Boolean
  fun createHandler(completionType: CompletionType, expectedText: String, prefix: String?): CodeCompletionHandlerBase

  companion object {
    private val EP_NAME = ExtensionPointName.create<CodeCompletionHandlerFactory>("com.intellij.cce.codeCompletionHandlerFactory")

    fun findCompletionHandlerFactory(project: Project, language: Language): CodeCompletionHandlerFactory? {
      return EP_NAME.getExtensionList(project).singleOrNull { it.isApplicable(language) }
    }
  }
}