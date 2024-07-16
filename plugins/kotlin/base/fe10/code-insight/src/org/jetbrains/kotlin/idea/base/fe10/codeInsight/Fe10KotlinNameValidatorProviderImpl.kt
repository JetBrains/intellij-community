// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidatorProvider
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.psi.KtDeclaration

// todo move to kotlin.code-insight.k2
internal class Fe10KotlinNameValidatorProviderImpl : KotlinNameValidatorProvider {

    override fun createNameValidator(
        container: PsiElement,
        target: KotlinNameSuggestionProvider.ValidatorTarget,
        anchor: PsiElement?,
        excludedDeclarations: List<KtDeclaration>,
    ): KotlinNameValidator = Fe10KotlinNewDeclarationNameValidator(
        container,
        anchor,
        target,
        excludedDeclarations,
    )
}