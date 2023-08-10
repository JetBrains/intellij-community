// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.ngram

import com.intellij.completion.ml.ngram.NGram.forgetTokens
import com.intellij.completion.ml.ngram.NGram.learnTokens
import com.intellij.completion.ml.ngram.NGram.lexPsiFile
import com.intellij.completion.ngram.slp.modeling.Model
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel
import com.intellij.completion.ngram.slp.modeling.runners.ModelRunner
import com.intellij.psi.PsiFile

internal class ModelRunnerWithCache(model: Model = JMModel()) : ModelRunner(model) {

  private val myCache = FilePath2Tokens(this)

  internal fun processFile(psiFile: PsiFile, filePath: String) {
    if (filePath in myCache) return
    val tokens = lexPsiFile(psiFile, TEXT_RANGE_LIMIT)
    myCache[filePath] = tokens
    learnTokens(tokens)
  }

  private class FilePath2Tokens(private val myModelRunner: ModelRunner) : LinkedHashMap<String, List<String>>() {

    override fun removeEldestEntry(eldest: Map.Entry<String?, List<String>?>): Boolean {
      if (size > CACHE_SIZE) {
        eldest.value?.let { myModelRunner.forgetTokens(it) }
        return true
      }
      return false
    }
  }

  companion object {
    private const val CACHE_SIZE = 9
    private const val TEXT_RANGE_LIMIT = 16 * 1024 // 32 KB of chars
  }
}