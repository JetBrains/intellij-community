// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.api

import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement

interface CompletionDummyIdentifierProviderService {
    fun correctPositionForStringTemplateEntry(context: CompletionInitializationContext): Boolean
    /**
     * If caret is at the parameter declaration, sets replacement offset to the end of the type reference.
     * It is required for correct completion of parameter name with type.
     */
    fun correctPositionForParameter(context: CompletionInitializationContext)
    fun provideDummyIdentifier(context: CompletionInitializationContext): String
    fun provideSuffixToAffectParsingIfNecessary(element: PsiElement): String

    companion object {
        fun getInstance(): CompletionDummyIdentifierProviderService = service()
    }
}