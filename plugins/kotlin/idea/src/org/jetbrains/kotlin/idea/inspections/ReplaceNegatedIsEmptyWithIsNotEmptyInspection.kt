// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.EmptinessCheckFunctionUtils
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ReplaceNegatedIsEmptyWithIsNotEmptyInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return prefixExpressionVisitor(fun(expression) {
            if (expression.operationToken != KtTokens.EXCL) return
            val base = expression.baseExpression?.let { KtPsiUtil.deparenthesize(it) } ?: return
            val from = base.calleeText() ?: return
            val to = EmptinessCheckFunctionUtils.invertFunctionCall(base, ::fqName)?.calleeText() ?: return
            holder.registerProblem(
                expression,
                KotlinBundle.message("replace.negated.0.with.1", from, to),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                ReplaceNegatedIsEmptyWithIsNotEmptyQuickFix(from, to)
            )
        })
    }

    object Util {
        fun KtQualifiedExpression.invertSelectorFunction(bindingContext: BindingContext? = null): KtQualifiedExpression? {
            return EmptinessCheckFunctionUtils.invertFunctionCall(this) { fqName(it, bindingContext) } as? KtQualifiedExpression
        }
    }
}

class ReplaceNegatedIsEmptyWithIsNotEmptyQuickFix(private val from: String, private val to: String) : LocalQuickFix {
    override fun getName() = KotlinBundle.message("replace.negated.0.with.1", from, to)

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val prefixExpression = descriptor.psiElement as? KtPrefixExpression ?: return
        val baseExpression = KtPsiUtil.deparenthesize(prefixExpression.baseExpression) ?: return
        val psiFactory = KtPsiFactory(project)
        val newExpression = when (baseExpression) {
            is KtCallExpression -> psiFactory.createExpression("$to()")
            is KtQualifiedExpression -> psiFactory.createExpressionByPattern("$0.$to()", baseExpression.receiverExpression)
            else -> return
        }
        prefixExpression.replaced(newExpression)
    }
}

private fun fqName(callExpression: KtCallExpression, bindingContext: BindingContext? = null): FqName? {
    return callExpression
        .getResolvedCall(bindingContext ?: callExpression.analyze(BodyResolveMode.PARTIAL))
        ?.resultingDescriptor
        ?.fqNameSafe
}

private fun KtExpression.calleeText(): String? {
    val call = (this as? KtQualifiedExpression)?.callExpression ?: this as? KtCallExpression ?: return null
    return call.calleeExpression?.text
}