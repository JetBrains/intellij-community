// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.isToString
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.canPlaceAfterSimpleNameEntry

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class RemoveToStringInStringTemplateInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        dotQualifiedExpressionVisitor(fun(expression) {
            if (expression.parent !is KtBlockStringTemplateEntry) return
            if (expression.receiverExpression is KtSuperExpression) return
            val selectorExpression = expression.selectorExpression ?: return
            if (!expression.isToString()) return

            holder.registerProblem(
                selectorExpression,
                KotlinBundle.message("redundant.tostring.call.in.string.template"),
                RemoveToStringFix()
            )
        })
}

class RemoveToStringFix : LocalQuickFix {
    override fun getName() = KotlinBundle.message("remove.to.string.fix.text")
    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement.parent as? KtDotQualifiedExpression ?: return

        val receiverExpression = element.receiverExpression
        val templateEntry = element.parent
        if (receiverExpression is KtNameReferenceExpression &&
            templateEntry != null &&
            canPlaceAfterSimpleNameEntry(templateEntry.nextSibling)
        ) {
            val factory = KtPsiFactory(templateEntry)
            templateEntry.replace(factory.createSimpleNameStringTemplateEntry(receiverExpression.getReferencedName()))
        } else {
            element.replace(receiverExpression)
        }
    }
}
