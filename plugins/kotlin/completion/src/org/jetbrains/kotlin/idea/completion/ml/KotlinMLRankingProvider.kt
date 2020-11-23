/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.completion.ml

import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.lang.Language
import org.jetbrains.kotlin.idea.completion.KotlinIdeaCompletionBundle

class KotlinMLRankingProvider : CatBoostJarCompletionModelProvider(
    KotlinIdeaCompletionBundle.message("kotlin.ml.completion.model"),
    "kotlin_features",
    "kotlin_model"
) {
    override fun isLanguageSupported(language: Language): Boolean = language.id.equals("kotlin", ignoreCase = true)
}