// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators

import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

@Deprecated("Please don't use this for new inspections. Use `KotlinApplicableDiagnosticInspection` or `KotlinApplicableDiagnosticInspectionWithContext` instead.")
abstract class AbstractKotlinDiagnosticBasedInspection<PSI : KtElement, DIAGNOSTIC : KtDiagnosticWithPsi<PSI>, INPUT : KotlinApplicatorInput>(
    elementType: KClass<PSI>,
) : AbstractKotlinApplicatorBasedInspection<PSI, INPUT>(elementType) {
    abstract fun getDiagnosticType(): KClass<DIAGNOSTIC>

    abstract fun getInputByDiagnosticProvider(): KotlinApplicatorInputByDiagnosticProvider<PSI, DIAGNOSTIC, INPUT>

    final override fun getInputProvider(): KotlinApplicatorInputProvider<PSI, INPUT> = inputProvider { psi ->
        val diagnostics = psi.getDiagnostics(KtDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
        val diagnosticType = getDiagnosticType()
        val suitableDiagnostics = diagnostics.filterIsInstance(diagnosticType.java)
        val diagnostic = suitableDiagnostics.firstOrNull() ?: return@inputProvider null
        // TODO handle case with multiple diagnostics on single element
        with(getInputByDiagnosticProvider()) { createInfo(diagnostic) }
    }
}


