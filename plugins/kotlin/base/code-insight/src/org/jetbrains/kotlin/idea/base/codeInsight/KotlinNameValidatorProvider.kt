// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.psi.KtElement

typealias KotlinNameValidator = (@NonNls String) -> Boolean

@ApiStatus.Internal
interface KotlinNameValidatorProvider {

    fun createNameValidator(
        container: KtElement,
        target: KotlinNameSuggestionProvider.ValidatorTarget, // todo move here
        anchor: PsiElement? = null,
    ): KotlinNameValidator
}