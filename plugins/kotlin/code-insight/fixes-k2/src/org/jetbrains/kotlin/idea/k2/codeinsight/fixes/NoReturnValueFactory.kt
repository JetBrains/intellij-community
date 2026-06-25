// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

internal object NoReturnValueFactory {
    val noReturnValue =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnValueNotUsed ->
            createQuickFix(diagnostic.psi)
        }

    val noReturnValueCoercion =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnValueNotUsedCoercion ->
            createQuickFix(diagnostic.psi)
        }

    private fun createQuickFix(
        element: KtElement,
    ): List<UnderscoreValueFix> {
        val expression = element.getExpressionToWrap() ?: return emptyList()
        val parent = expression as? KtCallableReferenceExpression ?: (findParentOrOuterMostParentheses(expression) ?: return emptyList())
        if (!isSuitableParent(parent)) return emptyList()
        return listOf(UnderscoreValueFix(expression, parent.createSmartPointer()))
    }

    private fun KtElement.getExpressionToWrap(): KtElement? {
        val parent = parent
        return when (parent) {
            is KtCallExpression if parent.calleeExpression == this -> parent
            is KtCallableReferenceExpression if parent.callableReference == this -> parent
            else -> this
        }
    }

    private fun findParentOrOuterMostParentheses(element: KtElement): PsiElement? {
        var parent: PsiElement? = element.parent
        while (parent is KtParenthesizedExpression || parent is KtBinaryExpression) {
            val parentOfParent = parent.parent
            if (parentOfParent !is KtParenthesizedExpression && parentOfParent !is KtBinaryExpression) break
            parent = parentOfParent
        }
        return parent
    }

    private fun isSuitableParent(element: PsiElement): Boolean =
        element is KtBlockExpression
                || element is KtParenthesizedExpression
                || element is KtBinaryExpression
                || element is KtWhenEntry
                || element is KtContainerNode
                || element is KtCallableReferenceExpression

    private class UnderscoreValueFix(
        element: KtElement,
        private val parentPointer: SmartPsiElementPointer<PsiElement>,
    ) : KotlinPsiUpdateModCommandAction.ElementContextless<KtElement>(element) {
        override fun getFamilyName(): String = KotlinBundle.message("explicitly.ignore.return.value")

        override fun invoke(
            context: ActionContext,
            element: KtElement,
            updater: ModPsiUpdater,
        ) {
            val parent = parentPointer.element ?: return
            val factory = KtPsiFactory(element.project)
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
                is KtCallableReferenceExpression -> {
                    val referencedName = parent.callableReference.text
                    val receiverText = parent.receiverExpression?.text
                    val callText = if (receiverText != null) "$receiverText.$referencedName()" else "$referencedName()"
                    factory.createExpression("{ val _ = $callText }")
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
                    throw KotlinExceptionWithAttachments("Unknown parent class: ${parent?.javaClass?.name}.")
                        .withPsiAttachment("element.kt", element)
                        .withPsiAttachment("file.kt", element.containingFile)
                }
            }
            return newExpression
        }

    }
}