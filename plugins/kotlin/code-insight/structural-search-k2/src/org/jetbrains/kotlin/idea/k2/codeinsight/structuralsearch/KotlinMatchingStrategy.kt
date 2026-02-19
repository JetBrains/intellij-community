// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch

import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtPackageDirective

object KotlinMatchingStrategy : MatchingStrategy {
    override fun continueMatching(start: PsiElement?): Boolean = start?.language == KotlinLanguage.INSTANCE

    override fun shouldSkip(element: PsiElement?, elementToMatchWith: PsiElement?): Boolean = when {
        element is KtPackageDirective || element?.parent is KtPackageDirective -> true
        else -> false
    }
}