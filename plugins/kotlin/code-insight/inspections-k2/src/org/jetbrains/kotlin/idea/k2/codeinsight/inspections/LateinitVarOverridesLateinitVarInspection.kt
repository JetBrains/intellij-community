// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.propertyVisitor

internal class LateinitVarOverridesLateinitVarInspection : KotlinApplicableInspectionBase<KtProperty, Unit>() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = propertyVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtProperty): Boolean =
        element.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
                element.hasModifier(KtTokens.LATEINIT_KEYWORD) &&
                element.isVar

    override fun getApplicableRanges(element: KtProperty): List<TextRange> =
        ApplicabilityRanges.declarationName(element)

    override fun KaSession.prepareContext(element: KtProperty): Unit? {
        val symbol = element.symbol as? KaPropertySymbol ?: return null
        return symbol.allOverriddenSymbols
            .filterIsInstance<KaKotlinPropertySymbol>()
            .any { !it.isVal && it.isLateInit }
            .asUnit
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtProperty,
        context: Unit,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor = createProblemDescriptor(
        element,
        rangeInElement,
        KotlinBundle.message("title.lateinit.var.overrides.lateinit.var"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        onTheFly,
    )
}
