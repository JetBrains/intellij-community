// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.JavaCollectionsStaticMethodInspectionUtils.Utils
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.dotQualifiedExpressionVisitor

// TODO merge with ReplaceJavaStaticMethodWithKotlinAnalogInspection
class JavaCollectionsStaticMethodInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return dotQualifiedExpressionVisitor(fun(expression) {
            val (methodName, firstArg) = getTargetMethodOnMutableList(expression) ?: return
            holder.registerProblem(
                expression,
                KotlinBundle.message("java.collections.static.method.call.should.be.replaced.with.kotlin.stdlib"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                ReplaceWithStdLibFix(methodName, firstArg.text)
            )
        })
    }

    private fun getTargetMethodOnMutableList(expression: KtDotQualifiedExpression): Pair<String, KtValueArgument>? =
        Utils.getTargetMethod(expression) { type -> analyze(expression) { Utils.isMutableListOrSubtype(type) } }
}

private class ReplaceWithStdLibFix(private val methodName: String, private val receiver: String) : LocalQuickFix {
    override fun getName() = KotlinBundle.message("replace.with.std.lib.fix.text", receiver, methodName)

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val expression = descriptor.psiElement as? KtDotQualifiedExpression ?: return
        val callExpression = expression.callExpression ?: return
        val valueArguments = callExpression.valueArguments
        val firstArg = valueArguments.getOrNull(0)?.getArgumentExpression() ?: return
        val secondArg = valueArguments.getOrNull(1)?.getArgumentExpression()
        val factory = KtPsiFactory(project)
        val newExpression = if (secondArg != null) {
            if (methodName == "sort") {
                factory.createExpressionByPattern("$0.sortWith(Comparator $1)", firstArg, secondArg.text)
            } else {
                factory.createExpressionByPattern("$0.$methodName($1)", firstArg, secondArg)
            }
        } else {
            factory.createExpressionByPattern("$0.$methodName()", firstArg)
        }
        expression.replace(newExpression)
    }
}
