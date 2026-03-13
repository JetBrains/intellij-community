// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.EmptinessCheckFunctionUtils
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionWithLabel
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTreeVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.qualifiedExpressionVisitor


abstract class AbstractUselessCallInspection : AbstractKotlinInspection() {
    protected abstract val conversions: List<Conversion<KtQualifiedExpression>>

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = qualifiedExpressionVisitor(fun(expression: KtQualifiedExpression) {
        val descriptor = conversions.firstNotNullOfOrNull {
            it.createProblemDescriptor(holder.manager, expression, isOnTheFly)
        } ?: return

        holder.registerProblem(descriptor)
    })

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

    protected interface Conversion<in T : KtElement> {
        @ApiStatus.NonExtendable
        fun createProblemDescriptor(
            manager: InspectionManager,
            element: T,
            isOnTheFly: Boolean,
        ): ProblemDescriptor?
    }

    protected interface QualifiedFunctionCallConversion : Conversion<KtQualifiedExpression> {
        val targetCallableId: CallableId

        @ApiStatus.NonExtendable
        override fun createProblemDescriptor(
            manager: InspectionManager,
            element: KtQualifiedExpression,
            isOnTheFly: Boolean
        ): ProblemDescriptor? {
            val selector = element.selectorExpression as? KtCallExpression ?: return null
            val calleeExpression = selector.calleeExpression ?: return null
            if (calleeExpression.text != targetCallableId.callableName.asString()) return null

            analyze(element) {
                val resolvedCall = calleeExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return null
                val resolvedCallableId = resolvedCall.symbol.callableId ?: return null
                if (resolvedCallableId != targetCallableId) return null

                return createProblemDescriptor(manager, element, calleeExpression, isOnTheFly)
            }
        }

        context(_: KaSession)
        fun createProblemDescriptor(
            manager: InspectionManager,
            expression: KtQualifiedExpression,
            calleeExpression: KtExpression,
            isOnTheFly: Boolean,
        ): ProblemDescriptor?
    }

    protected companion object {

        fun topLevelCallableId(packagePath: String, functionName: String): CallableId {
            return CallableId(FqName.topLevel(Name.identifier(packagePath)), Name.identifier(functionName))
        }

        fun KtQualifiedExpression.invertSelectorFunction(): KtQualifiedExpression? {
            return EmptinessCheckFunctionUtils.invertFunctionCall(this) as? KtQualifiedExpression
        }
    }
}