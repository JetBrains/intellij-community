// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddReifiedToTypeParameterOfFunctionFix
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object AddReifiedToTypeParameterOfFunctionFixFactory {

    val addReifiedToTypeParameterOfFunctionFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.TypeParameterAsReified ->
        val parameter = diagnostic.typeParameter.psi as? KtTypeParameter ?: return@ModCommandBased emptyList()
        val function = parameter.getStrictParentOfType<KtNamedFunction>() ?: return@ModCommandBased emptyList()

        listOf(
            AddReifiedToTypeParameterOfFunctionFix(parameter, function)
        )
    }
}
