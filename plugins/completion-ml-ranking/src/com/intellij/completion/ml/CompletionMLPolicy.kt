// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension

interface CompletionMLPolicy {
    companion object {
        val INSTANCE = LanguageExtension<CompletionMLPolicy>("com.intellij.completion.ml.ranking.policy")

        fun disableMLRanking(language: Language, parameters: CompletionParameters): Boolean {
            val policy = INSTANCE.forLanguage(language) ?: return false
            return policy.disableMLRanking(parameters)
        }
    }

    fun disableMLRanking(parameters: CompletionParameters): Boolean
}