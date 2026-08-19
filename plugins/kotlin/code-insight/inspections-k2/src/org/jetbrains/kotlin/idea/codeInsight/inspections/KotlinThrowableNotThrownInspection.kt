// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.search.searches.ReferencesSearch
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.expressions.isUsedAsExpression
import org.jetbrains.kotlin.analysis.api.expressions.isUsedAsResultOfLambda
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.types.builtinTypes
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.isNullable
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes

private const val NAME_HINT_EXCEPTION: String = "Exception"
private const val NAME_HINT_ERROR: String = "Error"

internal class KotlinThrowableNotThrownInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = callExpressionVisitor { callExpression ->
        val calleeExpression = callExpression.calleeExpression ?: return@callExpressionVisitor
        if (!calleeExpression.text.let { it.contains(NAME_HINT_EXCEPTION) || it.contains(NAME_HINT_ERROR) })
            return@callExpressionVisitor
        if (TestUtils.isInTestSourceContent(callExpression))
            return@callExpressionVisitor

        analyze(callExpression) {
            val functionSymbol = callExpression.resolveToCall()?.successfulFunctionCallOrNull()?.symbol ?: return@callExpressionVisitor
            val type = functionSymbol.returnType
            if (type.classId == KaStandardTypeClassIds.NOTHING || type.isNullable) return@callExpressionVisitor
            if (!type.isSubtypeOf(builtinTypes.throwable)) return@callExpressionVisitor
            if (callExpression.isUsed()) return@callExpressionVisitor
            val description = if (functionSymbol is KaConstructorSymbol) {
                KotlinBundle.message("throwable.instance.0.is.not.thrown", calleeExpression.text)
            } else {
                KotlinBundle.message("result.of.0.call.is.not.thrown", calleeExpression.text)
            }
            holder.registerProblem(calleeExpression, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
    }
}

@OptIn(KaExperimentalApi::class)
context(_: KaSession)
private fun KtExpression.isUsed(): Boolean {
    if (!isUsedAsExpression) return false
    if (isUsedAsResultOfLambda) return true
    val property = getParentOfTypes(
        strict = true,
        KtThrowExpression::class.java,
        KtReturnExpression::class.java,
        KtProperty::class.java
    ) as? KtProperty ?: return true
    return !property.isLocal || ReferencesSearch.search(property).any()
}
