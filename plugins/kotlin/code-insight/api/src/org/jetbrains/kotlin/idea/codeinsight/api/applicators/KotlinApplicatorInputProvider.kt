// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession

/**
 * Resolves the code to provide [KotlinApplicator] some input
 */
@FileModifier.SafeTypeForPreview
abstract class KotlinApplicatorInputProvider<PSI : PsiElement, out INPUT : KotlinApplicatorInput> {
    /**
     * Provide input to the applicator, if returns `null` then the applicator is not applicable and will not be called
     * Guaranteed to be executed from read action, should not be called from EDT thread
     */
    abstract fun KtAnalysisSession.provideInput(element: PSI): INPUT?
}

private class KotlinApplicatorInputProviderImpl<PSI : PsiElement, out INPUT : KotlinApplicatorInput>(
    private val provideInput: KtAnalysisSession.(PSI) -> INPUT?
) : KotlinApplicatorInputProvider<PSI, INPUT>() {
    override fun KtAnalysisSession.provideInput(element: PSI): INPUT? = provideInput.invoke(this, element)
}

/**
 * Creates [KotlinApplicatorInputProvider]
 * The [provideInput] is guaranteed to be executed from read action, should not be called from EDT thread
 */
fun <PSI : PsiElement, INPUT : KotlinApplicatorInput> inputProvider(
    provideInput: KtAnalysisSession.(PSI) -> INPUT?
): KotlinApplicatorInputProvider<PSI, INPUT> =
    KotlinApplicatorInputProviderImpl(provideInput)