// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections.coroutines

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class DeferredResultUnusedInspection(@JvmField var standardOnly: Boolean = false) : AbstractKotlinInspection() {
    private fun isExpressionApplicable(expression: KtExpression): Boolean =
        expression is KtCallExpression && (!standardOnly || expression.calleeExpression?.text in shortNames)

    private fun shouldReportCall(resolvedCall: ResolvedCall<*>): Boolean {
        val resultingDescriptor = resolvedCall.resultingDescriptor
        val fqName = resultingDescriptor.fqNameOrNull()
        if (fqName in fqNamesThatShouldNotBeReported) return false

        return if (standardOnly) {
            fqName in fqNamesAll
        } else {
            val returnTypeClassifier = resultingDescriptor.returnType?.constructor?.declarationDescriptor
            val importableFqName = returnTypeClassifier?.importableFqName
            importableFqName == deferred || importableFqName == deferredExperimental
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
        // Then check by call
        val context = expression.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL_WITH_CFA)
        if (context == BindingContext.EMPTY || expression.isUsedAsExpression(context)) return false
        val resolvedCall = expression.getResolvedCall(context) ?: return false
        return shouldReportCall(resolvedCall)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        callExpressionVisitor(fun(expression) {
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

private val deferred = FqName("$COROUTINE_PACKAGE.Deferred")

private val deferredExperimental = FqName("$COROUTINE_EXPERIMENTAL_PACKAGE.Deferred")

private val fqNamesThatShouldNotBeReported =
    listOf("kotlin.test.assertNotNull", "kotlin.requireNotNull", "kotlin.checkNotNull").map { FqName(it) }
