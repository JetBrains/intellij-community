// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddConstModifierFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object ConstFixFactories {
    val addConstModifierFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NonConstValUsedInConstantExpression ->
            val expression = when (val psi = diagnostic.psi) {
                is KtReferenceExpression -> psi
                is KtQualifiedExpression -> psi.selectorExpression as? KtReferenceExpression
                else -> null
            } ?: return@ModCommandBased emptyList()

            val property: KtProperty = analyze(expression) {
                val propertySymbol = expression.mainReference.resolveToSymbol() ?: return@analyze null
                (propertySymbol.psi as? KtProperty)?.takeIf(::constModifierApplicable)
            } ?: return@ModCommandBased emptyList()

            val action = AddConstModifierFix(property).asIntention().asModCommandAction() ?: return@ModCommandBased emptyList()
            listOf(action)
        }
}

private fun KaSession.constModifierApplicable(property: KtProperty): Boolean {
    val isInsideObject = property.getStrictParentOfType<KtObjectDeclaration>() != null
    val type = property.returnType
    val initializer = property.initializer
    val constValue = initializer?.evaluate()

    return when {
        property.isVar -> false
        property.hasDelegate() -> false
        !property.isTopLevel && !isInsideObject -> false
        property.getter != null -> false
        type.isMarkedNullable -> false
        !type.isPrimitive && !type.isStringType -> false
        initializer == null -> false
        constValue == null || constValue is KaConstantValue.ErrorValue -> false
        else -> true
    }
}