// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.isArrayOfFunction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class ReplaceArrayOfWithLiteralInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = callExpressionVisitor(fun(expression) {
        val calleeExpression = expression.calleeExpression as? KtNameReferenceExpression ?: return

        when (val parent = expression.parent) {
            is KtValueArgument -> {
                if (parent.parent?.parent !is KtAnnotationEntry) return
                if (parent.getSpreadElement() != null && !parent.isNamed()) return
            }

            is KtParameter -> {
                val constructor = parent.parent?.parent as? KtPrimaryConstructor ?: return
                val containingClass = constructor.getContainingClassOrObject()
                if (!containingClass.isAnnotation()) return
            }

            else -> return
        }

        if (!expression.isArrayOfFunction()) return
        val calleeName = calleeExpression.getReferencedName()
        holder.registerProblem(
            calleeExpression,
            KotlinBundle.message("0.call.should.be.replaced.with.array.literal", calleeName),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            ReplaceWithArrayLiteralFix()
        )
    })

    private class ReplaceWithArrayLiteralFix : LocalQuickFix {
        override fun getFamilyName() = KotlinBundle.message("replace.with.array.literal.fix.family.name")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val calleeExpression = descriptor.psiElement as KtExpression
            val callExpression = calleeExpression.parent as KtCallExpression

            val valueArgument = callExpression.getParentOfType<KtValueArgument>(false)
            valueArgument?.getSpreadElement()?.delete()

            val arguments = callExpression.valueArguments
            val arrayLiteral = KtPsiFactory(project).buildExpression {
                appendFixedText("[")
                for ((index, argument) in arguments.withIndex()) {
                    appendExpression(argument.getArgumentExpression())
                    if (index != arguments.size - 1) {
                        appendFixedText(", ")
                    }
                }
                appendFixedText("]")
            } as KtCollectionLiteralExpression

            callExpression.replace(arrayLiteral)
        }
    }
}
