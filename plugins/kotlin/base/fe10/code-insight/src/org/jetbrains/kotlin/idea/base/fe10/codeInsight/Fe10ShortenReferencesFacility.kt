// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

internal class Fe10ShortenReferencesFacility : ShortenReferencesFacility {
    override fun shorten(file: KtFile, range: TextRange) {
        ShortenReferences.DEFAULT.process(file, range.startOffset, range.endOffset)
    }

    override fun shorten(element: KtElement) {
        ShortenReferences.DEFAULT.process(element)
    }
}