// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.common

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.textMatching.SimilarityScorer

object ContextSimilarityUtil {
  val LINE_SIMILARITY_SCORER_KEY: Key<SimilarityScorer> = Key.create("LINE_SIMILARITY_SCORER")
  val PARENT_SIMILARITY_SCORER_KEY: Key<SimilarityScorer> = Key.create("PARENT_SIMILARITY_SCORER")

  fun createLineSimilarityScorer(line: String): SimilarityScorer = SimilarityScorer(StringUtil.getWordsIn(line))

  fun createParentSimilarityScorer(element: PsiElement?): SimilarityScorer {
    var curElement = element
    val parents = mutableListOf<String>()
    while (curElement != null && curElement !is PsiFile) {
      if (curElement is PsiNamedElement) {
        curElement.name?.let { parents.add(it) }
      }
      curElement = curElement.parent
    }
    return SimilarityScorer(parents)
  }
}
