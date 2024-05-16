// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.psi.KtDeclaration

typealias KotlinNameValidator = (@NonNls String) -> Boolean

@ApiStatus.Internal
interface KotlinNameValidatorProvider {

    companion object {

        fun getInstance(): KotlinNameValidatorProvider = service()
    }

    fun createNameValidator(
        container: PsiElement,
        target: KotlinNameSuggestionProvider.ValidatorTarget, // todo move here
        anchor: PsiElement? = null,
        excludedDeclarations: List<KtDeclaration> = emptyList(),
    ): KotlinNameValidator
}