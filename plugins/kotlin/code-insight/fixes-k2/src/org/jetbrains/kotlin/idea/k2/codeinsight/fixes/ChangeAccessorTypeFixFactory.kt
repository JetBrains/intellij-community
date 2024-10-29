// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.types.Variance

internal object ChangeAccessorTypeFixFactory {

    val wrongGetterReturnTypeFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.WrongGetterReturnType ->
        getFixes(diagnostic.psi, diagnostic.expectedType)
    }

    val wrongSetterParameterTypeFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.WrongSetterParameterType ->
        getFixes(diagnostic.psi, diagnostic.expectedType)
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.getFixes(element: KtElement, expectedType: KaType): List<ChangeAccessorTypeFix> {
        val propertyAccessor = element.getParentOfType<KtPropertyAccessor>(
            strict = false,
            KtProperty::class.java,
        ) ?: return emptyList()

        val typePresentation = expectedType.render(
            renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
            position = Variance.IN_VARIANCE,
        )
        val typeSourceCode = expectedType.render(position = Variance.IN_VARIANCE)

        return listOf(
            ChangeAccessorTypeFix(propertyAccessor, typePresentation, typeSourceCode)
        )
    }
}
