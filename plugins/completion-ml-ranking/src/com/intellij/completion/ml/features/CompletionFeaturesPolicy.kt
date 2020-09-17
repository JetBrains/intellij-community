// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.features

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension

interface CompletionFeaturesPolicy {
    companion object {
        val Instance = LanguageExtension<CompletionFeaturesPolicy>("com.intellij.completion.ml.ranking.features.policy")

        fun useNgramModel(language: Language): Boolean {
            val policy = Instance.forLanguage(language) ?: return false
            return policy.useNgramModel()
        }
    }

    fun useNgramModel(): Boolean
}