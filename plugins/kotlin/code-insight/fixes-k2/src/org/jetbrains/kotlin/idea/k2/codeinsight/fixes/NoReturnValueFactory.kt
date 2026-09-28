// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.returnType
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

internal object NoReturnValueFactory {
    val noReturnValue =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnValueNotUsed ->
            createQuickFix(diagnostic.psi, addReturn = true)
        }

    val noReturnValueCoercion =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnValueNotUsedCoercion ->
            createQuickFix(diagnostic.psi, addReturn = false)
        }

    context(_: KaSession)
    private fun createQuickFix(
        element: KtElement,
        addReturn: Boolean,
    ): List<ModCommandAction> {
        val expression = element.getExpressionToWrap() ?: return emptyList()
        val parent = expression as? KtCallableReferenceExpression ?: (findParentOrOuterMostParentheses(expression) ?: return emptyList())
        if (!isSuitableParent(parent)) return emptyList()
        return buildList {
            if (addReturn) {
                addIfNotNull(createAddReturnFix(expression, parent))
            }
            add(UnderscoreValueFix(expression, parent.createSmartPointer()))
        }
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

    context(_: KaSession)
    private fun createAddReturnFix(
        expression: KtElement,
        parent: PsiElement,
    ): AddReturnKeywordFix? {
        val expressionToReturn = when (parent) {
            is KtParenthesizedExpression, is KtBinaryExpression -> parent
            else -> expression
        } as? KtExpression ?: return null
        if (!expressionToReturn.isLastStatementInBranch()) return null

        val function = expressionToReturn.getStrictParentOfType<KtNamedFunction>() ?: return null
        if (expressionToReturn.hasFunctionLiteralParentBefore(function)) return null

        val functionReturnType = function.returnType
        if (functionReturnType is KaErrorType || functionReturnType.classId == KaStandardTypeClassIds.UNIT) return null

        val expressionType = expressionToReturn.expressionType?.takeIf { it !is KaErrorType } ?: return null

        return if (expressionType.isSubtypeOf(functionReturnType)) {
            AddReturnKeywordFix(expressionToReturn)
        } else {
            null
        }
    }

    private fun KtExpression.isLastStatementInBranch(): Boolean {
        val parent = parent
        return when (parent) {
            is KtBlockExpression -> parent.statements.lastOrNull() === this && parent.isBranchBlock()
            is KtContainerNode -> true
            is KtWhenEntry -> parent.expression === this
            else -> false
        }
    }

    private fun KtBlockExpression.isBranchBlock(): Boolean =
        when (parent) {
            is KtCatchClause,
            is KtContainerNode,
            is KtTryExpression,
            is KtWhenEntry -> true
            else -> false
        }

    private fun KtExpression.hasFunctionLiteralParentBefore(function: KtNamedFunction): Boolean {
        var current = parent
        while (current != null && current !== function) {
            if (current is KtFunctionLiteral) return true
            current = current.parent
        }
        return current !== function
    }

    private class AddReturnKeywordFix(
        element: KtExpression,
    ) : KotlinPsiUpdateModCommandAction.ElementContextless<KtExpression>(element) {
        override fun getFamilyName(): String = KotlinBundle.message("fix.add.return.keyword")

        override fun invoke(
            context: ActionContext,
            element: KtExpression,
            updater: ModPsiUpdater,
        ) {
            val writableElement = updater.getWritable(element)
            writableElement.replace(KtPsiFactory(context.project).createExpression("return ${writableElement.text}"))
        }
    }

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
