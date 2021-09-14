// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.api

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi

sealed class HLInputByDiagnosticProvider<PSI : PsiElement, DIAGNOSTIC : KtDiagnosticWithPsi<PSI>, INPUT : HLApplicatorInput> {
    abstract fun KtAnalysisSession.createInfo(diagnostic: DIAGNOSTIC): INPUT?
}

private class HLInputByDiagnosticProviderImpl<PSI : PsiElement, DIAGNOSTIC : KtDiagnosticWithPsi<PSI>, INPUT : HLApplicatorInput>(
    private val createInfo: KtAnalysisSession.(DIAGNOSTIC) -> INPUT?
) : HLInputByDiagnosticProvider<PSI, DIAGNOSTIC, INPUT>() {
    override fun KtAnalysisSession.createInfo(diagnostic: DIAGNOSTIC): INPUT? =
        createInfo.invoke(this, diagnostic)
}

fun <PSI : PsiElement, DIAGNOSTIC : KtDiagnosticWithPsi<PSI>, INPUT : HLApplicatorInput> inputByDiagnosticProvider(
    createInfo: KtAnalysisSession.(DIAGNOSTIC) -> INPUT?
): HLInputByDiagnosticProvider<PSI, DIAGNOSTIC, INPUT> =
    HLInputByDiagnosticProviderImpl(createInfo)