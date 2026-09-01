// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.RemoveNameFromFunctionExpressionFix
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType

internal object AnonymousFunctionWithNameFixFactories {
    @OptIn(KaExperimentalApi::class)
    val removeNameFromFunctionExpressionFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AnonymousFunctionWithName ->
        val element = diagnostic.psi as? KtNamedFunction ?: return@ModCommandBased emptyList()
        var wereAutoLabelUsages = false
        val name = element.nameAsName ?: return@ModCommandBased emptyList()

        element.forEachDescendantOfType<KtReturnExpression> {
            if (!wereAutoLabelUsages && it.getLabelNameAsName() == name) {
                wereAutoLabelUsages = it.resolveSuccessfulSymbol()?.psi == element
            }
        }

        listOf(
            RemoveNameFromFunctionExpressionFix(element, wereAutoLabelUsages)
        )
    }
}
