// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.ngram

import com.intellij.completion.ngram.slp.modeling.runners.ModelRunner
import com.intellij.lang.Language
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Service(Service.Level.PROJECT)
class NGramModelRunnerManager(
  private val project: Project,
  private val coroutineScope: CoroutineScope
) {
  private val myModelRunners: MutableMap<String, ModelRunnerWithCache> = mutableMapOf()
  private val myMutex = Mutex()

  fun scheduleAnalysis(file: VirtualFile) {
    coroutineScope.launch {
      myMutex.withLock {
        smartReadAction(project) {
          if (file.isValid && !project.isDisposed) {
            val psiFile = PsiManager.getInstance(project).findFile(file)
            if (psiFile != null) {
              processFile(psiFile, psiFile.language)
            }
          }
        }
      }
    }
  }

  private fun processFile(psiFile: PsiFile, language: Language) {
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