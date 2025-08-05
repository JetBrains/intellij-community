// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.EmptinessCheckFunctionUtils
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*


abstract class AbstractUselessCallInspection : AbstractKotlinInspection() {
    protected abstract val uselessFqNames: Map<CallableId, Conversion>

    protected abstract val uselessNames: Set<String>

    context(_: KaSession)
    protected abstract fun QualifiedExpressionVisitor.suggestConversionIfNeeded(
        expression: KtQualifiedExpression,
        calleeExpression: KtExpression,
        conversion: Conversion
    )

    abstract class ScopedLabelVisitor(private val label: String) : KtTreeVisitor<Unit>() {
        private fun String.trimLabel() = trim('@').trim()

        override fun visitLabeledExpression(expression: KtLabeledExpression, data: Unit?): Void? {
            // The label has been overwritten, do not descend into children
            if (expression.getLabelName() == label) return null
            return super.visitLabeledExpression(expression, data)
        }

        override fun visitCallExpression(expression: KtCallExpression, data: Unit?): Void? {
            // The label has been overwritten, do not descend into children
            if (expression.calleeExpression?.text?.trimLabel() == label) return null
            return super.visitCallExpression(expression, data)
        }
    }

    inner class QualifiedExpressionVisitor internal constructor(val holder: ProblemsHolder, val isOnTheFly: Boolean) : KtVisitorVoid() {
        override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
            super.visitQualifiedExpression(expression)
            val selector = expression.selectorExpression as? KtCallExpression ?: return
            val calleeExpression = selector.calleeExpression ?: return
            if (calleeExpression.text !in uselessNames) return

            analyze(calleeExpression) {
                val resolvedCall = calleeExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return
                val callableId = resolvedCall.symbol.callableId ?: return
                val conversion = uselessFqNames[callableId] ?: return
                suggestConversionIfNeeded(expression, calleeExpression, conversion)
            }
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = QualifiedExpressionVisitor(holder, isOnTheFly)

    protected fun KtExpression.isUsingLabelInScope(labelName: String): Boolean {
        var usingLabel = false
        accept(object : KtTreeVisitor<Unit>() {
            override fun visitExpressionWithLabel(expression: KtExpressionWithLabel, data: Unit?): Void? {
                if (expression.getLabelName() == labelName) {
                    usingLabel = true
                }
                return super.visitExpressionWithLabel(expression, data)
            }
        })
        return usingLabel
    }

    protected sealed interface Conversion {
        data class Replace(val replacementName: String) : Conversion
        object Delete : Conversion
    }

    protected companion object {

        fun topLevelCallableId(packagePath: String, functionName: String): CallableId {
            return CallableId(FqName.topLevel(Name.identifier(packagePath)), Name.identifier(functionName))
        }

        fun Set<CallableId>.toShortNames() = mapTo(mutableSetOf()) { it.callableName.asString() }

        fun KtQualifiedExpression.invertSelectorFunction(): KtQualifiedExpression? {
            return EmptinessCheckFunctionUtils.invertFunctionCall(this) as? KtQualifiedExpression
        }
    }
}