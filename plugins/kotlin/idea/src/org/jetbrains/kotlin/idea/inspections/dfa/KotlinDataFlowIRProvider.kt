// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow
import com.intellij.codeInspection.dataFlow.lang.ir.DataFlowIRProvider
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class KotlinDataFlowIRProvider : DataFlowIRProvider {
    override fun createControlFlow(factory: DfaValueFactory, psiBlock: PsiElement): ControlFlow? {
        if (psiBlock !is KtExpression) return null
        return KtControlFlowBuilder(factory, psiBlock).buildFlow()
    }

    override fun unreachableSegments(startAnchor: PsiElement, unreachableElements: Set<PsiElement>): Collection<TextRange> =
        unreachableElements.mapNotNullTo(HashSet()) { element -> createRange(element, startAnchor, unreachableElements) }

    private val SHORT_CIRCUITING_TOKENS = TokenSet.create(KtTokens.ANDAND, KtTokens.OROR, KtTokens.ELVIS)

    private fun createRange(element: PsiElement, startAnchor: PsiElement, unreachableElements: Set<PsiElement>): TextRange? {
        val parent = element.parent
        val gParent = parent?.parent
        return when {
            parent is KtContainerNode &&
                    (gParent is KtIfExpression || gParent is KtWhileExpression || gParent is KtForExpression)
                    || parent is KtWhenEntry -> element.textRange
            parent is KtBinaryExpression && parent.right == element &&
                    SHORT_CIRCUITING_TOKENS.contains(parent.operationToken) ->
                parent.operationReference.textRange.union(element.textRange)
            parent is KtSafeQualifiedExpression && parent.selectorExpression == element ->
                parent.operationTokenNode.textRange.union(element.textRange)
            parent is KtBlockExpression -> {
                val prevExpression = PsiTreeUtil.skipWhitespacesAndCommentsBackward(element) as? KtExpression
                if (prevExpression != null && unreachableElements.contains(prevExpression)) null
                else {
                    val lastExpression = parent.statements.last()
                    if (lastExpression == null || prevExpression == null) null
                    else {
                        if (prevExpression is KtLoopExpression && PsiTreeUtil.isAncestor(prevExpression, startAnchor, false)) {
                            null
                        } else element.textRange.union(lastExpression.textRange)
                    }
                }
            }
            else -> null
        }
    }
}