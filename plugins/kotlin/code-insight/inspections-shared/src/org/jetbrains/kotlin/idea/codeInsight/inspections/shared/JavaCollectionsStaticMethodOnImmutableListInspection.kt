// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.JavaCollectionsStaticMethodInspectionUtils.Utils
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.dotQualifiedExpressionVisitor

class JavaCollectionsStaticMethodOnImmutableListInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return dotQualifiedExpressionVisitor(fun(expression) {
            val (methodName, firstArg) = getTargetMethodOnImmutableList(expression) ?: return
            holder.registerProblem(
                expression.callExpression?.calleeExpression ?: expression,
                KotlinBundle.message("call.of.java.mutator.0.on.immutable.kotlin.collection.1", methodName, firstArg.text)
            )
        })
    }

    private fun getTargetMethodOnImmutableList(expression: KtDotQualifiedExpression): Pair<String, KtValueArgument>? =
        Utils.getTargetMethod(expression) { type ->
            analyze(expression) {
                Utils.isListOrSubtype(type) && Utils.isMutableListOrSubtype(type)
            }
        }
}
