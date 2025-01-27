// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.k2.refactoring.util.isRedundantUnit
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.referenceExpressionVisitor

class RedundantUnitExpressionInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = referenceExpressionVisitor(fun(expression) {
        if (isRedundantUnit(expression)) {
            holder.registerProblem(
                expression,
                KotlinBundle.message("redundant.unit"),
                RemoveRedundantUnitFix()
            )
        }
    })
}


private class RemoveRedundantUnitFix : LocalQuickFix {
    override fun getName() = KotlinBundle.message("remove.redundant.unit.fix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        (descriptor.psiElement as? KtReferenceExpression)?.delete()
    }
}