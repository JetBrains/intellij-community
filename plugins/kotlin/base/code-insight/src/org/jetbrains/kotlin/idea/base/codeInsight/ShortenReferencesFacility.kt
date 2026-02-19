// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

interface ShortenReferencesFacility {
    fun shorten(file: KtFile, range: TextRange, shortenOptions: ShortenOptionsForIde)
    fun shorten(element: KtElement, shortenOptions: ShortenOptionsForIde): PsiElement?

    /* ---------------------------------------------------------------------- */
    //region Default overloads for Java callees

    fun shorten(file: KtFile, range: TextRange) {
        shorten(file, range, ShortenOptionsForIde.DEFAULT)
    }

    fun shorten(element: KtElement): PsiElement? {
        return shorten(element, ShortenOptionsForIde.DEFAULT)
    }

    companion object {
        fun getInstance(): ShortenReferencesFacility = service()
    }
}