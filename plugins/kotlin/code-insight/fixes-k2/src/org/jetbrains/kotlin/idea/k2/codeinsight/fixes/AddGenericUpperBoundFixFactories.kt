// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ModCommandAction
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.types.Variance

internal object AddGenericUpperBoundFixFactories {

    val upperBoundViolatedFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UpperBoundViolated ->
        listOfNotNull(
            createActionIfAvailable(diagnostic.expectedUpperBound, diagnostic.actualUpperBound)
        )
    }

    val upperBoundViolatedBasedOnJavaAnnotationsFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UpperBoundViolatedBasedOnJavaAnnotations ->
            listOfNotNull(
                createActionIfAvailable(diagnostic.expectedUpperBound, diagnostic.actualUpperBound)
            )
        }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.createActionIfAvailable(
        expectedUpperBound: KaType,
        actualUpperBound: KaType,
    ): ModCommandAction? {
        val typeParameter = (actualUpperBound as? KaTypeParameterType)?.symbol?.psi as? KtTypeParameter ?: return null
        if (typeParameter.name == null || typeParameter.extendsBound != null) return null

        return AddGenericUpperBoundFix(
            element = typeParameter,
            fqName = expectedUpperBound.render(
                renderer = KaTypeRendererForSource.WITH_QUALIFIED_NAMES,
                position = Variance.IN_VARIANCE,
            ),
            shortName = expectedUpperBound.render(
                renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
                position = Variance.IN_VARIANCE,
            )
        )
    }
}
