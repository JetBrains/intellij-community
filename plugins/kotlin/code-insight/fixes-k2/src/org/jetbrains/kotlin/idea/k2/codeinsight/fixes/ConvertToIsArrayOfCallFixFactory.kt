// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ConvertToIsArrayOfCallFix
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.types.Variance

internal object ConvertToIsArrayOfCallFixFactory {
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.createQuickFix(isExpression: KtIsExpression, type: KaType): ConvertToIsArrayOfCallFix? {
        if (!isExpression.platform.isJvm()) return null
        val arrayArgumentType = type.arrayElementType ?: return null
        val lhsType = isExpression.leftHandSide.expressionType ?: return null
        val lhsIsNullable = lhsType.isMarkedNullable
        val lhsIsArray = lhsType.isArrayOrPrimitiveArray
        val arrayArgumentTypeText = arrayArgumentType.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT)

        return ConvertToIsArrayOfCallFix(
            element = isExpression,
            lhsIsNullable = lhsIsNullable,
            lhsIsArray = lhsIsArray,
            arrayArgumentTypeText = arrayArgumentTypeText
        )
    }

    val cannotCheckForErased = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.CannotCheckForErased ->
        val isExpression = diagnostic.psi.parent as? KtIsExpression ?: return@ModCommandBased emptyList()
        listOfNotNull(createQuickFix(isExpression, diagnostic.type))
    }
}