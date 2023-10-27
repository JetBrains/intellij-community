// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

internal class Fe10ShortenReferencesFacility : ShortenReferencesFacility {
    private fun createFe10Shortener(commonShortenOptions: ShortenOptions): ShortenReferences {
        val matchingFe10Options = ShortenReferences.Options.DEFAULT.copy(
            removeThis = commonShortenOptions.removeThis
        )

        // do not create a new instance of reference shortener if the options are default ones
        if (matchingFe10Options == ShortenReferences.Options.DEFAULT) return ShortenReferences.DEFAULT

        return ShortenReferences(options = { matchingFe10Options })
    }

    override fun shorten(file: KtFile, range: TextRange, shortenOptions: ShortenOptions) {
        createFe10Shortener(shortenOptions).process(file, range.startOffset, range.endOffset)
    }

    override fun shorten(element: KtElement, shortenOptions: ShortenOptions): KtElement {
        return createFe10Shortener(shortenOptions).process(element)
    }
}