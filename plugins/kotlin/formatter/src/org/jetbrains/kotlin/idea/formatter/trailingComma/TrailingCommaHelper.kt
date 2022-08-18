// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.formatter.trailingComma

import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.util.isComma
import org.jetbrains.kotlin.idea.util.isLineBreak
import org.jetbrains.kotlin.idea.util.leafIgnoringWhitespace
import org.jetbrains.kotlin.idea.util.leafIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.nextLeaf
import org.jetbrains.kotlin.psi.psiUtil.prevLeaf
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.utils.addToStdlib.cast

object TrailingCommaHelper {
    fun findInvalidCommas(commaOwner: KtElement): List<PsiElement> = commaOwner.firstChild
        ?.siblings(withItself = false)
        ?.filter { it.isComma }
        ?.filter {
            it.prevLeaf(true)?.isLineBreak() == true || it.leafIgnoringWhitespace(false) != it.leafIgnoringWhitespaceAndComments(false)
        }?.toList().orEmpty()

    fun trailingCommaExistsOrCanExist(psiElement: PsiElement, settings: CodeStyleSettings): Boolean =
        TrailingCommaContext.create(psiElement).commaExistsOrMayExist(settings.kotlinCustomSettings)

    fun trailingCommaExists(commaOwner: KtElement): Boolean = when (commaOwner) {
        is KtFunctionLiteral -> commaOwner.valueParameterList?.trailingComma != null
        is KtWhenEntry -> commaOwner.trailingComma != null
        is KtDestructuringDeclaration -> commaOwner.trailingComma != null
        else -> trailingCommaOrLastElement(commaOwner)?.isComma == true
    }

    fun trailingCommaOrLastElement(commaOwner: KtElement): PsiElement? {
        val lastChild = commaOwner.lastSignificantChild ?: return null
        val withSelf = when (PsiUtil.getElementType(lastChild)) {
            KtTokens.COMMA -> return lastChild
            in RIGHT_BARRIERS -> false
            else -> true
        }

        return lastChild.getPrevSiblingIgnoringWhitespaceAndComments(withSelf)?.takeIf {
            PsiUtil.getElementType(it) !in LEFT_BARRIERS
        }?.takeIfIsNotError()
    }

    /**
     * @return true if [commaOwner] has a trailing comma and hasn't a line break before the first or after the last element
     */
    fun lineBreakIsMissing(commaOwner: KtElement): Boolean {
        if (!trailingCommaExists(commaOwner)) return false

        val first = elementBeforeFirstElement(commaOwner)
        if (first?.nextLeaf(true)?.isLineBreak() == false) return true

        val last = elementAfterLastElement(commaOwner)
        return last?.prevLeaf(true)?.isLineBreak() == false
    }

    fun elementBeforeFirstElement(commaOwner: KtElement): PsiElement? = when (commaOwner) {
        is KtParameterList -> {
            val parent = commaOwner.parent
            if (parent is KtFunctionLiteral) parent.lBrace else commaOwner.leftParenthesis
        }
        is KtWhenEntry -> commaOwner.parent.cast<KtWhenExpression>().openBrace
        is KtDestructuringDeclaration -> commaOwner.lPar
        else -> commaOwner.firstChild?.takeIfIsNotError()
    }

    fun elementAfterLastElement(commaOwner: KtElement): PsiElement? = when (commaOwner) {
        is KtParameterList -> {
            val parent = commaOwner.parent
            if (parent is KtFunctionLiteral) parent.arrow else commaOwner.rightParenthesis
        }
        is KtWhenEntry -> commaOwner.arrow
        is KtDestructuringDeclaration -> commaOwner.rPar
        else -> commaOwner.lastChild?.takeIfIsNotError()
    }

    private fun PsiElement.takeIfIsNotError(): PsiElement? = takeIf { !PsiTreeUtil.hasErrorElements(it) }

    private val RIGHT_BARRIERS = TokenSet.create(KtTokens.RBRACKET, KtTokens.RPAR, KtTokens.RBRACE, KtTokens.GT, KtTokens.ARROW)
    private val LEFT_BARRIERS = TokenSet.create(KtTokens.LBRACKET, KtTokens.LPAR, KtTokens.LBRACE, KtTokens.LT)
    private val PsiElement.lastSignificantChild: PsiElement?
        get() = when (this) {
            is KtWhenEntry -> arrow
            is KtDestructuringDeclaration -> rPar
            else -> lastChild
        }
}