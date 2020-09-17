// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.ngram

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension

interface NgramCompletionPolicy {
    companion object {
        val Instance = LanguageExtension<NgramCompletionPolicy>("com.intellij.completion.ml.ranking.ngram.policy")

        fun useNgramModel(language: Language): Boolean {
            val policy = Instance.forLanguage(language) ?: return false
            return policy.useNgramModel()
        }
    }

    fun useNgramModel(): Boolean
}