// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.api

import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.openapi.components.service

interface CompletionDummyIdentifierProviderService {
    fun correctPositionForStringTemplateEntry(context: CompletionInitializationContext): Boolean
    fun provideDummyIdentifier(context: CompletionInitializationContext): String

    companion object {
        fun getInstance(): CompletionDummyIdentifierProviderService = service()
    }
}