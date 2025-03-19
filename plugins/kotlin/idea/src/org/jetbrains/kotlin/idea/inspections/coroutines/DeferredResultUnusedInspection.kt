// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections.coroutines

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.inspections.AbstractResultUnusedChecker
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull

class DeferredResultUnusedInspection(@JvmField var standardOnly: Boolean = false) : AbstractResultUnusedChecker(
    expressionChecker = fun(expression, inspection): Boolean =
        inspection is DeferredResultUnusedInspection && expression is KtCallExpression &&
                (!inspection.standardOnly || expression.calleeExpression?.text in shortNames),
    callChecker = fun(resolvedCall, inspection): Boolean {
        if (inspection !is DeferredResultUnusedInspection) return false

        val resultingDescriptor = resolvedCall.resultingDescriptor
        val fqName = resultingDescriptor.fqNameOrNull()
        if (fqName in fqNamesThatShouldNotBeReported) return false

        return if (inspection.standardOnly) {
            fqName in fqNamesAll
        } else {
            val returnTypeClassifier = resultingDescriptor.returnType?.constructor?.declarationDescriptor
            val importableFqName = returnTypeClassifier?.importableFqName
            importableFqName == deferred || importableFqName == deferredExperimental
        }
    }
) {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        callExpressionVisitor(fun(expression) {
            if (!check(expression)) return
            holder.registerProblem(expression.calleeExpression ?: expression, KotlinBundle.message("deferred.result.is.never.used"))
        })

  override fun getOptionsPane() = pane(
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
