// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.api

import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.fir.api.applicator.HLApplicatorInputProvider
import org.jetbrains.kotlin.idea.fir.api.applicator.inputProvider
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

abstract class AbstractHLDiagnosticBasedInspection<PSI : KtElement, DIAGNOSTIC : KtDiagnosticWithPsi<PSI>, INPUT : HLApplicatorInput>(
    elementType: KClass<PSI>,
    private val diagnosticType: KClass<DIAGNOSTIC>,
) : AbstractHLInspection<PSI, INPUT>(elementType) {
    abstract val inputByDiagnosticProvider: HLInputByDiagnosticProvider<PSI, DIAGNOSTIC, INPUT>

    final override val inputProvider: HLApplicatorInputProvider<PSI, INPUT> = inputProvider { psi ->
        val diagnostics = psi.getDiagnostics(KtDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
        val suitableDiagnostics = diagnostics.filterIsInstance(diagnosticType.java)
        val diagnostic = suitableDiagnostics.firstOrNull() ?: return@inputProvider null
        // TODO handle case with multiple diagnostics on single element
        with(inputByDiagnosticProvider) { createInfo(diagnostic) }
    }
}


