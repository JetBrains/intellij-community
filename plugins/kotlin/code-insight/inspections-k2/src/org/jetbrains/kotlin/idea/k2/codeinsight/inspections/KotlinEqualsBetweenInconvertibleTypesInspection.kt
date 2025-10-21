// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isPrimitive
import org.jetbrains.kotlin.analysis.api.components.isStringType
import org.jetbrains.kotlin.analysis.api.components.withNullability
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.isEnum
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class KotlinEqualsBetweenInconvertibleTypesInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) = callExpressionVisitor(
        fun(call) {
            val callee = call.calleeExpression as? KtSimpleNameExpression ?: return
            val identifier = callee.getReferencedNameAsName()
            if (identifier != OperatorNameConventions.EQUALS) return
            val receiver = call.getQualifiedExpressionForSelector()?.receiverExpression ?: return
            val argument = call.valueArguments.singleOrNull()?.getArgumentExpression() ?: return
            analyze(call) {
                val receiverType = receiver.getTypeIfComparable() ?: return
                val argumentType = argument.getTypeIfComparable() ?: return
                if (!receiverType.semanticallyEquals(argumentType)) {
                    holder.registerProblem(callee, KotlinBundle.message("equals.between.objects.of.inconvertible.types"))
                }
            }
        }
    )

    context(_: KaSession)
    private fun KtExpression.getTypeIfComparable(): KaType? {
        val type = expressionType?.withNullability(false)
        return type?.takeIf { it.isPrimitive || it.isStringType || it.isEnum() }
    }
}