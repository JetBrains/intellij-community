// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidatorProvider
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

internal class K2NameValidatorProviderImpl : KotlinNameValidatorProvider {

    @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
    override fun createNameValidator(
        container: PsiElement,
        target: KotlinNameSuggestionProvider.ValidatorTarget,
        anchor: PsiElement?,
        excludedDeclarations: List<KtDeclaration>,
    ): KotlinNameValidator = { name ->
        val context = (anchor ?: container)
            .parentsWithSelf
            .filterIsInstance<KtElement>()
            .first()

        val validator = KotlinDeclarationNameValidator(
            visibleDeclarationsContext = context,
            checkVisibleDeclarationsContext = anchor != null,
            target = target,
            excludedDeclarations = excludedDeclarations
        )


        allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                analyze(context) {
                    validator.validate(name)
                }
            }
        }
    }
}