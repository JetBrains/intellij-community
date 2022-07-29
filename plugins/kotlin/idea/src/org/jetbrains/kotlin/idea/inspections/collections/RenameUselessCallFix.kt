// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.collections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType

class RenameUselessCallFix(val newName: String) : LocalQuickFix {
    override fun getName() = KotlinBundle.message("rename.useless.call.fix.text", newName)

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        (descriptor.psiElement as? KtQualifiedExpression)?.let {
            val factory = KtPsiFactory(it)
            val selectorCallExpression = it.selectorExpression as? KtCallExpression
            val calleeExpression = selectorCallExpression?.calleeExpression ?: return
            calleeExpression.replaced(factory.createExpression(newName))
            selectorCallExpression.renameGivenReturnLabels(factory, calleeExpression.text, newName)
        }
    }

    private fun KtCallExpression.renameGivenReturnLabels(factory: KtPsiFactory, labelName: String, newName: String) {
        val lambdaExpression = lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return
        val bodyExpression = lambdaExpression.bodyExpression ?: return

        bodyExpression.forEachDescendantOfType<KtReturnExpression> {
            if (it.getLabelName() != labelName) return@forEachDescendantOfType

            it.replaced(
                factory.createExpressionByPattern(
                    "return@$0 $1",
                    newName,
                    it.returnedExpression ?: ""
                )
            )
        }
    }
}