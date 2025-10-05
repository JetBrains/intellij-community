// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.golf.CompletionGolfStrategy
import com.intellij.cce.processor.EvaluationRootProcessor
import com.intellij.cce.visitor.exceptions.NullRootException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class CodeFragmentBuilder {
  companion object {
    fun create(project: Project, languageName: String?, featureName: String, strategy: EvaluationStrategy): CodeFragmentBuilder {
      val language = languageName?.let { Language.resolve(languageName) }

      return when {
        strategy is CompletionGolfStrategy -> LineCompletionFragmentBuilder(project, language, featureName, strategy.mode, false)
        language != Language.ANOTHER -> CodeFragmentFromPsiBuilder(project, language)
        else -> CodeFragmentFromTextBuilder()
      }
    }
  }

  protected fun findRoot(code: CodeFragment, rootProcessor: EvaluationRootProcessor): CodeFragment {
    rootProcessor.process(code)
    return rootProcessor.getRoot() ?: throw NullRootException(code.path)
  }

  abstract fun build(file: VirtualFile, rootProcessor: EvaluationRootProcessor, featureName: String): CodeFragment?
}
