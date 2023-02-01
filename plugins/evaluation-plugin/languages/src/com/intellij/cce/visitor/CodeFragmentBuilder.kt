package com.intellij.cce.visitor

import com.intellij.cce.actions.CompletionGolfMode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.cce.processor.EvaluationRootProcessor
import com.intellij.cce.visitor.exceptions.NullRootException
import com.intellij.cce.core.Language
import com.intellij.cce.core.CodeFragment

abstract class CodeFragmentBuilder {
  companion object {
    fun create(project: Project, languageName: String, completionGolf: CompletionGolfMode?): CodeFragmentBuilder {
      val language = Language.resolve(languageName)

      return when {
        completionGolf != null -> CompletionGolfFragmentBuilder(project, language, completionGolf)
        language != Language.ANOTHER -> CodeFragmentFromPsiBuilder(project, language)
        else -> CodeFragmentFromTextBuilder()
      }
    }
  }

  protected fun findRoot(code: CodeFragment, rootProcessor: EvaluationRootProcessor): CodeFragment {
    rootProcessor.process(code)
    return rootProcessor.getRoot() ?: throw NullRootException(code.path)
  }

  abstract fun build(file: VirtualFile, rootProcessor: EvaluationRootProcessor): CodeFragment
}
