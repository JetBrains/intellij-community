// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

interface ShortenReferencesFacility {
    fun shorten(file: KtFile, range: TextRange)
    fun shorten(element: KtElement)

    companion object {
        fun getInstance(): ShortenReferencesFacility = service()
    }
}