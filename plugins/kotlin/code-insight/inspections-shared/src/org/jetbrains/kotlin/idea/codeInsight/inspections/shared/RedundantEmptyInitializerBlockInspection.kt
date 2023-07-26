// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.classInitializerVisitor

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

internal class RedundantEmptyInitializerBlockInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = classInitializerVisitor(fun(initializer) {
        val body = initializer.body as? KtBlockExpression ?: return
        if (body.statements.isNotEmpty()) return
        holder.registerProblem(
            initializer,
            KotlinBundle.message("redundant.empty.initializer.block"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            RemoveInitializerBlockFix()
        )
    })

    private class RemoveInitializerBlockFix : LocalQuickFix {
        override fun getName() = KotlinBundle.message("remove.initializer.block.fix.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            (descriptor.psiElement as? KtClassInitializer)?.delete()
        }
    }
}