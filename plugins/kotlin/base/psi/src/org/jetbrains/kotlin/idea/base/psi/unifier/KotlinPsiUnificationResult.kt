// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.psi.unifier

import org.jetbrains.kotlin.psi.KtElement

sealed interface KotlinPsiUnificationResult {
    object Failure : KotlinPsiUnificationResult {
        override val isMatched: Boolean
            get() = false
    }

    sealed interface Success<T> : KotlinPsiUnificationResult {
        val range: KotlinPsiRange
        val substitution: Map<T, KtElement>

        override val isMatched: Boolean
            get() = true
    }

    class StrictSuccess<T>(
        override val range: KotlinPsiRange,
        override val substitution: Map<T, KtElement>
    ) : Success<T>

    class WeakSuccess<T>(
        override val range: KotlinPsiRange,
        override val substitution: Map<T, KtElement>,
        val weakMatches: Map<KtElement, KtElement>
    ) : Success<T>

    val isMatched: Boolean
}