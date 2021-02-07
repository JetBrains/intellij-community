// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.api.applicator

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.frontend.api.KtAnalysisSession

abstract class HLApplicatorInputProvider<PSI : PsiElement, out INPUT : HLApplicatorInput> {
    abstract fun KtAnalysisSession.provideInput(element: PSI): INPUT?
}

private class HLApplicatorInputProviderImpl<PSI : PsiElement, out INPUT : HLApplicatorInput>(
    private val provideInput: KtAnalysisSession.(PSI) -> INPUT?
) : HLApplicatorInputProvider<PSI, INPUT>() {
    override fun KtAnalysisSession.provideInput(element: PSI): INPUT? = provideInput.invoke(this, element)
}

fun <PSI : PsiElement, INPUT : HLApplicatorInput> inputProvider(
    provideInput: KtAnalysisSession.(PSI) -> INPUT?
): HLApplicatorInputProvider<PSI, INPUT> =
    HLApplicatorInputProviderImpl(provideInput)