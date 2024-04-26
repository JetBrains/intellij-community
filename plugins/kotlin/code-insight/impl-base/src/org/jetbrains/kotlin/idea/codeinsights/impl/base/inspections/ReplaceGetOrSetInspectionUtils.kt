// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.util.OperatorNameConventions

object ReplaceGetOrSetInspectionUtils {
    private val operatorNames: Set<Name>
        get() = setOf(OperatorNameConventions.GET, OperatorNameConventions.SET)

    fun looksLikeGetOrSetOperatorCall(expression: KtDotQualifiedExpression): Boolean {
        val callExpression = expression.selectorExpression as? KtCallExpression ?: return false
        val calleeName = (callExpression.calleeExpression as? KtSimpleNameExpression)?.getReferencedNameAsName() ?: return false
        if (calleeName !in operatorNames) return false
        if (callExpression.typeArgumentList != null) return false
        val arguments = callExpression.valueArguments
        if (arguments.isEmpty()) return false
        if (arguments.any { it.isNamed() || it.isSpread }) return false
        return true
    }

    fun replaceGetOrSetWithPropertyAccessor(expression: KtDotQualifiedExpression, isSet: Boolean, moveCaret: ((Int) -> Unit)?) {
        val callExpression = (expression.selectorExpression as? KtCallExpression) ?: return
        val newExpression = KtPsiFactory(expression.project).buildExpression {
            val allArguments = callExpression.valueArguments

            appendExpression(expression.receiverExpression)

            appendFixedText("[")

            // rcv.get(index1, index2, ...) -> rcv[index1, index2, ...]
            // rcv.set(index1, index2, ..., value) -> rcv[index1, index2, ...] = value
            val arguments = if (isSet) allArguments.dropLast(1) else allArguments
            appendExpressions(arguments.map { it.getArgumentExpression() })

            appendFixedText("]")

            if (isSet) {
                appendFixedText("=")
                appendExpression(allArguments.last().getArgumentExpression())
            }
        }

        val newElement = expression.replace(newExpression)

        if (moveCaret != null) {
            moveCaret(moveCaret, isSet, newElement)
        }
    }

    private fun moveCaret(moveCaret: ((Int) -> Unit), isSet: Boolean, newElement: PsiElement) {
        val arrayAccessExpression = if (isSet) {
            newElement.getChildOfType()
        } else {
            newElement as? KtArrayAccessExpression
        } ?: return

        arrayAccessExpression.leftBracket?.startOffset?.let { moveCaret.invoke(it) }
    }
}