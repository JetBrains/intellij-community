// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.intentions.isRange
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.dotQualifiedExpressionVisitor

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class ReplaceRangeStartEndInclusiveWithFirstLastInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return dotQualifiedExpressionVisitor(fun(expression: KtDotQualifiedExpression) {
            val selectorExpression = expression.selectorExpression ?: return
            if (selectorExpression.text != "start" && selectorExpression.text != "endInclusive") return

            val resolvedCall = expression.resolveToCall() ?: return
            val containing = resolvedCall.resultingDescriptor.containingDeclaration as? ClassDescriptor ?: return
            if (!containing.isRange()) return

            if (selectorExpression.text == "start") {
                holder.registerProblem(
                    expression,
                    KotlinBundle.message("could.be.replaced.with.unboxed.first"),
                    ReplaceIntRangeStartWithFirstQuickFix()
                )
            } else if (selectorExpression.text == "endInclusive") {
                holder.registerProblem(
                    expression,
                    KotlinBundle.message("could.be.replaced.with.unboxed.last"),
                    ReplaceIntRangeEndInclusiveWithLastQuickFix()
                )
            }
        })
    }
}

class ReplaceIntRangeStartWithFirstQuickFix : LocalQuickFix {
    override fun getName() = KotlinBundle.message("replace.int.range.start.with.first.quick.fix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as KtDotQualifiedExpression
        val selector = element.selectorExpression ?: return
        selector.replace(KtPsiFactory(project).createExpression("first"))
    }
}

class ReplaceIntRangeEndInclusiveWithLastQuickFix : LocalQuickFix {
    override fun getName() = KotlinBundle.message("replace.int.range.end.inclusive.with.last.quick.fix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as KtDotQualifiedExpression
        val selector = element.selectorExpression ?: return
        selector.replace(KtPsiFactory(project).createExpression("last"))
    }
}