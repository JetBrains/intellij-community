// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinKtDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.ReplaceArrayEqualityOpWithArraysEqualsInspection.Context
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import kotlin.reflect.KClass

internal class ReplaceArrayEqualityOpWithArraysEqualsInspection :
    KotlinKtDiagnosticBasedInspectionBase<KtExpression, KaFirDiagnostic.ArrayEqualityOperatorCanBeReplacedWithContentEquals, Context>() {

    override val diagnosticFilter: KaDiagnosticCheckerFilter = KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS

    override val diagnosticType: KClass<KaFirDiagnostic.ArrayEqualityOperatorCanBeReplacedWithContentEquals>
        get() = KaFirDiagnostic.ArrayEqualityOperatorCanBeReplacedWithContentEquals::class

    data class Context(val isNotEqualOperator: Boolean)

    override fun KaSession.prepareContextByDiagnostic(
        element: KtExpression,
        diagnostic: KaFirDiagnostic.ArrayEqualityOperatorCanBeReplacedWithContentEquals,
    ): Context? {
        return (element as? KtBinaryExpression)?.operationToken?.let {
            when (it) {
                KtTokens.EXCLEQ -> Context(isNotEqualOperator = true)
                KtTokens.EQEQ -> Context(isNotEqualOperator = false)
                else -> null
            }
        }
    }

    override fun getProblemDescription(
        element: KtExpression,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("dangerous.array.comparison")

    override fun createQuickFix(
        element: KtExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtExpression> = object : KotlinModCommandQuickFix<KtExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.with.content.equals")

        override fun getName(): String = if (context.isNotEqualOperator) {
            KotlinBundle.message("replace.not.equal.with.content.equals")
        } else {
            KotlinBundle.message("replace.equal.with.content.equals")
        }

        override fun applyFix(
            project: Project,
            element: KtExpression,
            updater: ModPsiUpdater,
        ) {
            if (element !is KtBinaryExpression) return
            val right = element.right ?: return
            val left = element.left ?: return
            val template = buildString {
                if (context.isNotEqualOperator) append("!")
                append("$0.contentEquals($1)")
            }
            element.replace(KtPsiFactory(project).createExpressionByPattern(template, left, right))
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = expressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }
}
