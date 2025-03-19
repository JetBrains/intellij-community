// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

internal class SymbolBasedShortenReferencesFacility : ShortenReferencesFacility {
    override fun shorten(file: KtFile, range: TextRange, shortenOptions: ShortenOptions) {
        shortenReferencesInRange(file, range, shortenOptions)
    }

    override fun shorten(element: KtElement, shortenOptions: ShortenOptions): PsiElement? {
        return shortenReferences(element, shortenOptions)
    }
}