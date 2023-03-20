// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

internal class SymbolBasedShortenReferencesFacility : ShortenReferencesFacility {
    override fun shorten(file: KtFile, range: TextRange) {
        shortenReferencesInRange(file, range)
    }

    override fun shorten(element: KtElement) {
        shortenReferences(element)
    }
}