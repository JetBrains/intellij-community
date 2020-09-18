// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension

interface CompletionMLPolicy {
    companion object {
        private val INSTANCE = LanguageExtension<CompletionMLPolicy>("com.intellij.completion.ml.ranking.policy")

        fun isReRankingDisabled(language: Language, parameters: CompletionParameters): Boolean {
            return INSTANCE.allForLanguageOrAny(language).any { it.isReRankingDisabled(parameters) }
        }
    }

    fun isReRankingDisabled(parameters: CompletionParameters): Boolean
}