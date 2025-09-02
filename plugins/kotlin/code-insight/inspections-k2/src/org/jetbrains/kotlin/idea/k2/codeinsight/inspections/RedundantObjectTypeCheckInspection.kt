// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.*

internal class RedundantObjectTypeCheckInspection :
    KotlinApplicableInspectionBase.Simple<KtIsExpression, RedundantObjectTypeCheckInspection.Context>() {

    data class Context(val isNegated: Boolean)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitIsExpression(expression: KtIsExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(
        element: KtIsExpression,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("redundant.type.checks.for.object")

    override fun getApplicableRanges(element: KtIsExpression): List<TextRange> =
        listOf(element.operationReference.textRangeInParent)

    override fun isApplicableByPsi(element: KtIsExpression): Boolean =
        element.typeReference != null

    override fun KaSession.prepareContext(element: KtIsExpression): Context? {
        val typeReference = element.typeReference ?: return null
        val typeSymbol = typeReference.type.symbol as? KaNamedClassSymbol ?: return null

        if (!typeSymbol.classKind.isObject) return null

        // Data objects should not be compared by reference (===).
        // While data objects are typically singletons, in rare cases multiple instances might exist at runtime
        // (e.g., via reflection or serialization), and they should still be considered equal.
        if (typeSymbol.isData) return null

        return Context(element.isNegated)
    }

    override fun createQuickFix(
        element: KtIsExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtIsExpression> = object : KotlinModCommandQuickFix<KtIsExpression>() {

        private val isOperator = if (context.isNegated) "!is" else "is"
        private val equality = if (context.isNegated) "!==" else "==="

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.equality.fix.text", isOperator, equality)

        override fun applyFix(
            project: Project,
            element: KtIsExpression,
            updater: ModPsiUpdater,
        ) {
            val typeReference = element.typeReference ?: return
            val factory = KtPsiFactory(project)
            val newElement = factory.createExpressionByPattern("$0 $1 $2", element.leftHandSide, equality, typeReference.text)
            element.replace(newElement)
        }
    }
}
