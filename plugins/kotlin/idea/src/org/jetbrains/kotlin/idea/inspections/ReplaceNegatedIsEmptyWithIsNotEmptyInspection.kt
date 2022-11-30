// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getLastParentOfTypeInRow
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class ReplaceNegatedIsEmptyWithIsNotEmptyInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return qualifiedExpressionVisitor(fun(expression) {
            if (expression.getWrappingPrefixExpressionIfAny()?.operationToken != KtTokens.EXCL) return
            val calleeExpression = expression.callExpression?.calleeExpression ?: return
            val from = calleeExpression.text
            val to = expression.invertSelectorFunction()?.callExpression?.calleeExpression?.text ?: return
            holder.registerProblem(
                calleeExpression,
                KotlinBundle.message("replace.negated.0.with.1", from, to),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                ReplaceNegatedIsEmptyWithIsNotEmptyQuickFix(from, to)
            )
        })
    }

    companion object {
        fun KtQualifiedExpression.invertSelectorFunction(bindingContext: BindingContext? = null): KtQualifiedExpression? {
            val callExpression = callExpression ?: return null
            val fromFunctionName = callExpression.calleeExpression?.text ?: return null
            val (fromFunctionFqNames, toFunctionName) = functionNames[fromFunctionName] ?: return null
            val context = bindingContext ?: analyze(BodyResolveMode.PARTIAL)
            if (fromFunctionFqNames.none { callExpression.isCalling(it, context) }) return null
            return KtPsiFactory(project).createExpressionByPattern(
                "$0.$toFunctionName()",
                receiverExpression,
                reformat = false
            ) as? KtQualifiedExpression
        }
    }
}

class ReplaceNegatedIsEmptyWithIsNotEmptyQuickFix(private val from: String, private val to: String) : LocalQuickFix {
    override fun getName() = KotlinBundle.message("replace.negated.0.with.1", from, to)

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val qualifiedExpression = descriptor.psiElement.getStrictParentOfType<KtQualifiedExpression>() ?: return
        val prefixExpression = qualifiedExpression.getWrappingPrefixExpressionIfAny() ?: return
        prefixExpression.replaced(
            KtPsiFactory(project).createExpressionByPattern(
                "$0.$to()",
                qualifiedExpression.receiverExpression
            )
        )
    }
}

private fun PsiElement.getWrappingPrefixExpressionIfAny() =
    (getLastParentOfTypeInRow<KtParenthesizedExpression>() ?: this).parent as? KtPrefixExpression

private val packages = listOf(
    "java.util.ArrayList",
    "java.util.HashMap",
    "java.util.HashSet",
    "java.util.LinkedHashMap",
    "java.util.LinkedHashSet",
    "kotlin.collections",
    "kotlin.collections.List",
    "kotlin.collections.Set",
    "kotlin.collections.Map",
    "kotlin.collections.MutableList",
    "kotlin.collections.MutableSet",
    "kotlin.collections.MutableMap",
    "kotlin.text"
)

private val functionNames: Map<String, Pair<List<FqName>, String>> by lazy {
    mapOf(
        "isEmpty" to Pair(packages.map { FqName("$it.isEmpty") }, "isNotEmpty"),
        "isNotEmpty" to Pair(packages.map { FqName("$it.isNotEmpty") }, "isEmpty"),
        "isBlank" to Pair(listOf(FqName("kotlin.text.isBlank")), "isNotBlank"),
        "isNotBlank" to Pair(listOf(FqName("kotlin.text.isNotBlank")), "isBlank"),
    )
}
