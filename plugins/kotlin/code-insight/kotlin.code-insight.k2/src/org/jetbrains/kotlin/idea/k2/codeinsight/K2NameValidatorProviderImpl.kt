// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidatorProvider
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement

internal class K2NameValidatorProviderImpl : KotlinNameValidatorProvider {

    override fun createNameValidator(
        container: KtElement,
        target: KotlinNameSuggestionProvider.ValidatorTarget,
        anchor: PsiElement?,
        excludedDeclarations: List<KtDeclaration>,
    ): KotlinNameValidator = { name ->
        val validator = KotlinDeclarationNameValidator(
            visibleDeclarationsContext = container,
            checkVisibleDeclarationsContext = anchor != null,
            target = target,
        )

        analyze(container) {
            validator.validate(name)
        }
    }
}