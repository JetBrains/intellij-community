// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.ml

import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.internal.ml.completion.DecoratingItemsPolicy
import com.intellij.lang.Language
import com.intellij.openapi.application.ex.ApplicationInfoEx
import org.jetbrains.kotlin.idea.completion.KotlinIdeaCompletionBundle

class KotlinMLRankingProvider : CatBoostJarCompletionModelProvider(
    KotlinIdeaCompletionBundle.message("kotlin.ml.completion.model"),
    "kotlin_features",
    "kotlin_model"
) {
    override fun isLanguageSupported(language: Language): Boolean = language.id.equals("kotlin", ignoreCase = true)

    override fun isEnabledByDefault(): Boolean = ApplicationInfoEx.getInstanceEx().isEAP

    override fun getDecoratingPolicy(): DecoratingItemsPolicy = DecoratingItemsPolicy.Composite(
        DecoratingItemsPolicy.ByAbsoluteThreshold(3.0),
        DecoratingItemsPolicy.ByRelativeThreshold(2.0)
    )
}