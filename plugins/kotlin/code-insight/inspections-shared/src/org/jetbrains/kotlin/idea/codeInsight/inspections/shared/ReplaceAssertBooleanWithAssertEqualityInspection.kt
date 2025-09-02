// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal class ReplaceAssertBooleanWithAssertEqualityInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, ReplaceAssertBooleanWithAssertEqualityInspection.Context>() {

    data class Context(
        val assertion: String,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean =
        element.extractAssertionInfo() != null

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val assertionInfo = element.extractAssertionInfo(analysisSession = this) ?: return null
        val replacementAssertion = assertionMap[assertionInfo] ?: return null

        return Context(replacementAssertion)
    }

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("replace.assert.boolean.with.assert.equality")

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.assert.boolean.with.assert.equality")

        override fun getName(): String =
            KotlinBundle.message("replace.with.0", context.assertion)

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            val file = element.containingKtFile
            val valueArguments = element.valueArguments
            val condition = valueArguments.firstOrNull()?.getArgumentExpression() as? KtBinaryExpression ?: return
            val left = condition.left ?: return
            val right = condition.right ?: return

            val factory = KtPsiFactory(project)
            val replaced = if (valueArguments.size == 2) {
                val message = valueArguments[1].getArgumentExpression() ?: return
                element.replaced(
                    factory.createExpressionByPattern(
                        "${context.assertion}($0, $1, $2)",
                        left,
                        right,
                        message,
                    )
                )
            } else {
                element.replaced(
                    factory.createExpressionByPattern(
                        "${context.assertion}($0, $1)",
                        left,
                        right,
                    )
                )
            }

            ShortenReferencesFacility.getInstance().shorten(replaced)
            optimizeImports(file)
        }
    }
}

private fun optimizeImports(file: KtFile) {
    KotlinOptimizeImportsFacility.getInstance()
        .analyzeImports(file)
        ?.unusedImports
        ?.forEach { it.delete() }
}

private fun KtCallExpression.extractAssertionInfo(analysisSession: KaSession? = null): Pair<String, IElementType>? {
    val assertionName = (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return null
    if (assertionName !in assertions) return null

    if (valueArguments.size != 1 && valueArguments.size != 2) return null

    val condition = valueArguments.first().getArgumentExpression() as? KtBinaryExpression ?: return null
    val left = condition.left ?: return null
    val right = condition.right ?: return null

    val operationToken = condition.operationToken
    if (operationToken != KtTokens.EQEQ && operationToken != KtTokens.EQEQEQ) return null

    if (analysisSession == null) return assertionName to operationToken

    return with(analysisSession) {
        val callableSymbol = resolveToCall()?.successfulFunctionCallOrNull()?.symbol as? KaCallableSymbol ?: return null
        val containingPackage = callableSymbol.callableId?.packageName?.asString()
        if (containingPackage != kotlinTestPackage) return null

        val leftType = left.expressionType ?: return null
        val rightType = right.expressionType ?: return null
        if (!leftType.isSubtypeOf(rightType) && !rightType.isSubtypeOf(leftType)) return null

        assertionName to operationToken
    }
}

private const val kotlinTestPackage: String = "kotlin.test"
private val assertions: Set<String> = setOf("assertTrue", "assertFalse")

private val assertionMap: Map<Pair<String, KtSingleValueToken>, String> = mapOf(
    Pair("assertTrue", KtTokens.EQEQ) to "${kotlinTestPackage}.assertEquals",
    Pair("assertTrue", KtTokens.EQEQEQ) to "${kotlinTestPackage}.assertSame",
    Pair("assertFalse", KtTokens.EQEQ) to "${kotlinTestPackage}.assertNotEquals",
    Pair("assertFalse", KtTokens.EQEQEQ) to "${kotlinTestPackage}.assertNotSame"
)
