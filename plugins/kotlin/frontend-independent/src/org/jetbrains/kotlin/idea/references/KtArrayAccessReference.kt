// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.references

import com.intellij.psi.MultiRangeReference
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.util.OperatorNameConventions

abstract class KtArrayAccessReference(
    expression: KtArrayAccessExpression
) : KtSimpleReference<KtArrayAccessExpression>(expression), MultiRangeReference {
    override val resolvesByNames: Collection<Name>
        get() = NAMES

    override fun getRangeInElement() = element.textRange.shiftRight(-element.textOffset)

    private fun getBracketRange(bracketToken: KtToken) = expression.indicesNode
        .node
        .findChildByType(bracketToken)
        ?.textRange
        ?.shiftRight(-expression.textOffset)

    override fun getRanges() = listOfNotNull(getBracketRange(KtTokens.LBRACKET), getBracketRange(KtTokens.RBRACKET))

    override fun canRename() = true

    override fun handleElementRename(newElementName: String): PsiElement? = doRenameImplicitConventionalCall(newElementName)

    protected abstract fun moveFunctionLiteralOutsideParentheses(callExpression: KtCallExpression)
    protected abstract fun canMoveLambdaOutsideParentheses(callExpression: KtCallExpression): Boolean
    protected abstract fun doRenameImplicitConventionalCall(newName: String?): KtExpression

    companion object {
        private val NAMES = listOf(OperatorNameConventions.GET, OperatorNameConventions.SET)
    }
}
