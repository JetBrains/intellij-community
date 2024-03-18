// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.ngram

import com.intellij.completion.ngram.slp.modeling.runners.ModelRunner
import com.intellij.lang.Language
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

@Service(Service.Level.PROJECT)
class NGramModelRunnerManager {

  private val myModelRunners: MutableMap<String, ModelRunnerWithCache> = mutableMapOf()

  fun processFile(psiFile: PsiFile, language: Language) {
    myModelRunners.getOrPut(language.id) { ModelRunnerWithCache() }.processFile(psiFile, psiFile.virtualFile?.path ?: return)
  }

  fun getModelRunnerForLanguage(language: Language): ModelRunner? {
    return myModelRunners[language.id]
  }

  companion object {
    fun getInstance(project: Project): NGramModelRunnerManager {
      return project.getService(NGramModelRunnerManager::class.java)
    }
  }
}