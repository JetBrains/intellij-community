// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.*

internal object NoReturnValueFactory {
    val noReturnValue =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnValueNotUsed ->
            createQuickFix(diagnostic.psi)
        }

    private fun createQuickFix(
        element: KtElement,
    ): List<UnderscoreValueFix> {
        return listOf(UnderscoreValueFix(element))
    }

    private class UnderscoreValueFix(
        element: KtElement,
    ) : PsiUpdateModCommandAction<KtElement>(element) {
        override fun getFamilyName(): String = KotlinBundle.message("explicitly.ignore.return.value")

        override fun invoke(
            context: ActionContext,
            element: KtElement,
            updater: ModPsiUpdater,
        ) {
            val factory = KtPsiFactory(element.project)
            val parent = findParentOrOuterMostParentheses(element)
            val newExpression = buildNewExpression(factory, element, parent)

            val elementToReplace = when (parent) {
                is KtParenthesizedExpression, is KtBinaryExpression -> parent
                else -> element
            }.let(updater::getWritable)

            elementToReplace.replace(newExpression)
        }

        private fun buildNewExpression(
            factory: KtPsiFactory,
            element: KtElement,
            parent: PsiElement?
        ): KtExpression {
            val baseExpressionText = "val _ = ${element.text}"
            val newExpression = when (parent) {
                is KtBlockExpression -> {
                    factory.createDeclaration(baseExpressionText)
                }
                is KtParenthesizedExpression if parent.parent !is KtContainerNode -> {
                    factory.createDeclaration("val _ = ${parent.text}")
                }
                is KtBinaryExpression -> {
                    factory.createDeclaration("val _ = ${parent.text}")
                }
                is KtParenthesizedExpression, is KtWhenEntry, is KtContainerNode -> {
                    factory.createExpression("{$baseExpressionText}")
                }
                else -> {
                    LOG.error("Unknown parent class: ${parent?.javaClass?.name}. The result expression could be incorrect.")
                    factory.createExpression("{$baseExpressionText}")
                }
            }
            return newExpression
        }

        private fun findParentOrOuterMostParentheses(element: KtElement): PsiElement? {
            var parent: PsiElement? = element.parent
            while (parent is KtParenthesizedExpression) {
                val parentOfParent = parent.parent
                if (parentOfParent !is KtParenthesizedExpression) break
                parent = parentOfParent
            }
            return parent
        }
    }
}

private val LOG = Logger.getInstance(NoReturnValueFactory::class.java)