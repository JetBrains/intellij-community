// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

internal class DeferredResultUnusedInspection(@JvmField var standardOnly: Boolean = false) : AbstractKotlinInspection() {
    private fun isExpressionApplicable(expression: KtExpression): Boolean =
        expression is KtCallExpression && (!standardOnly || expression.calleeExpression?.text in shortNames)

    private fun KaSession.shouldReportCall(call: KaFunctionCall<*>): Boolean {
        val callableId = call.partiallyAppliedSymbol.symbol.callableId?.asSingleFqName()
        if (callableId in fqNamesThatShouldNotBeReported) return false

        return if (standardOnly) {
            callableId in fqNamesAll
        } else {
            val returnTypeClassId = call.partiallyAppliedSymbol.signature.returnType.expandedSymbol?.classId?.asSingleFqName()
            returnTypeClassId == deferred || returnTypeClassId == deferredExperimental
        }
    }

    private fun check(expression: KtExpression): Boolean {
        // Check whatever possible by PSI
        if (!isExpressionApplicable(expression)) return false
        var current: PsiElement? = expression
        var parent: PsiElement? = expression.parent
        while (parent != null) {
            if (parent is KtBlockExpression || parent is KtFunction || parent is KtFile) break
            if (parent is KtValueArgument || parent is KtBinaryExpression || parent is KtUnaryExpression) return false
            if (parent is KtQualifiedExpression && parent.receiverExpression == current) return false
            // TODO: add when condition, if condition (later when it's applicable not only to Deferred)
            current = parent
            parent = parent.parent
        }
        // Then check by call using Analysis API
        analyze(expression) {
            if (expression.isUsedAsExpression) return false
            val call = expression.resolveToCall()?.singleFunctionCallOrNull() ?: return false
            return shouldReportCall(call)
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): KtVisitorVoid = callExpressionVisitor(fun(expression) {
        if (!check(expression)) return
        holder.registerProblem(expression.calleeExpression ?: expression, KotlinBundle.message("deferred.result.is.never.used"))
    })

    override fun getOptionsPane(): OptPane = pane(
        checkbox("standardOnly", KotlinBundle.message("reports.only.function.calls.from.kotlinx.coroutines")))
}

private const val COROUTINE_PACKAGE = "kotlinx.coroutines"

private const val COROUTINE_EXPERIMENTAL_PACKAGE = "kotlinx.coroutines.experimental"

private val shortNames = setOf("async")

private val fqNames: Set<FqName> = shortNames.mapTo(mutableSetOf()) { FqName("$COROUTINE_PACKAGE.$it") }

private val fqNamesExperimental: Set<FqName> = shortNames.mapTo(mutableSetOf()) { FqName("$COROUTINE_EXPERIMENTAL_PACKAGE.$it") }

private val fqNamesAll = fqNames + fqNamesExperimental

private val deferred: FqName = FqName("$COROUTINE_PACKAGE.Deferred")

private val deferredExperimental: FqName = FqName("$COROUTINE_EXPERIMENTAL_PACKAGE.Deferred")

private val fqNamesThatShouldNotBeReported =
    listOf("kotlin.test.assertNotNull", "kotlin.requireNotNull", "kotlin.checkNotNull").map { FqName(it) }
