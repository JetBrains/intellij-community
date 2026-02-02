// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections.AbstractUselessCallInspection.ScopedLabelVisitor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class RenameUselessCallFix(private val newName: String, private val invert: Boolean = false) : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): String = KotlinBundle.message("rename.redundant.call.fix.text", newName)

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val qualifiedExpression = element as? KtQualifiedExpression ?: return
        val psiFactory = KtPsiFactory(project)
        val selectorCallExpression = qualifiedExpression.selectorExpression as? KtCallExpression
        val calleeExpression = selectorCallExpression?.calleeExpression ?: return
        calleeExpression.replaced(psiFactory.createExpression(newName))
        selectorCallExpression.renameGivenReturnLabels(psiFactory, calleeExpression.text, newName)
        if (invert) qualifiedExpression.invert()
    }

    private fun KtCallExpression.renameGivenReturnLabels(factory: KtPsiFactory, labelName: String, newName: String) {
        val lambdaExpression = lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return
        val bodyExpression = lambdaExpression.bodyExpression ?: return
        val expectedLabelText = "@$labelName"

        val replacementMap = mutableMapOf<PsiElement, PsiElement>()
        bodyExpression.accept(object : ScopedLabelVisitor(labelName) {
            override fun visitReturnExpression(expression: KtReturnExpression, data: Unit?): Void? {
                expression.labeledExpression?.takeIf { it.text == expectedLabelText }?.let { labeledExpression ->
                    // We use a hack here to only replace the labels in the return expressions, to avoid having to replace the entire
                    // returned expression, which might contain elements that we also need to modify
                    val newReturn = factory.createExpressionByPattern("return@$0", newName).children.firstOrNull() as? KtContainerNode
                    if (newReturn == null) return@let
                    replacementMap[labeledExpression] = newReturn
                }
                return super.visitReturnExpression(expression, data)
            }
        })
        replacementMap.forEach { original, replacement ->
            original.replace(replacement)
        }
    }

    private fun KtQualifiedExpression.invert() {
        val parent = parent.safeAs<KtPrefixExpression>() ?: return
        val baseExpression = parent.baseExpression ?: return
        parent.replace(baseExpression)
    }
}
