// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.util.isLineBreak
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

fun isRedundantSemicolon(semicolon: PsiElement): Boolean {
    val nextLeaf = semicolon.nextLeaf { it !is PsiWhiteSpace && it !is PsiComment || it.isLineBreak() }
    val isAtEndOfLine = nextLeaf == null || nextLeaf.isLineBreak()
    if (!isAtEndOfLine) {
        //when there is no imports parser generates empty import list with no spaces
        if (semicolon.parent is KtPackageDirective && (nextLeaf as? KtImportList)?.imports?.isEmpty() == true) {
            return true
        }
        if (semicolon.prevLeaf { it !is PsiWhiteSpace && it !is PsiComment || it.isLineBreak() } is PsiWhiteSpace &&
            !semicolon.isBeforeLeftBrace()
        ) {
            return true
        }
        return false
    }

    if (semicolon.prevLeaf()?.node?.elementType == KtNodeTypes.ELSE) return false

    if (semicolon.parent is KtEnumEntry) return false

    (semicolon.parent.parent as? KtClass)?.let { clazz ->
        if (clazz.isEnum() && clazz.getChildrenOfType<KtEnumEntry>().isEmpty()) {
            if (semicolon.prevLeaf {
                    it !is PsiWhiteSpace && it !is PsiComment && !it.isLineBreak()
                }?.node?.elementType == KtTokens.LBRACE &&
                clazz.declarations.isNotEmpty()
            ) {
                //first semicolon in enum with no entries, but with some declarations
                return false
            }
        }
    }

    (semicolon.prevLeaf()?.parent as? KtLoopExpression)?.let {
        if (it !is KtDoWhileExpression && it.body == null)
            return false
    }

    semicolon.prevLeaf()?.parent?.safeAs<KtIfExpression>()?.also { ifExpression ->
        if (ifExpression.then == null)
            return false
    }

    if (nextLeaf.isBeforeLeftBrace()) {
        return false // case with statement starting with '{' and call on the previous line
    }

    return !isSemicolonRequired(semicolon)
}

private fun PsiElement?.isBeforeLeftBrace(): Boolean {
    return this?.nextLeaf {
        it !is PsiWhiteSpace && it !is PsiComment && it.getStrictParentOfType<KDoc>() == null &&
                it.getStrictParentOfType<KtAnnotationEntry>() == null
    }?.node?.elementType == KtTokens.LBRACE
}

private fun isSemicolonRequired(semicolon: PsiElement): Boolean {
    if (isRequiredForCompanion(semicolon)) {
        return true
    }

    val prevSibling = semicolon.getPrevSiblingIgnoringWhitespaceAndComments()
    val nextSibling = semicolon.getNextSiblingIgnoringWhitespaceAndComments()

    if (prevSibling.safeAs<KtNameReferenceExpression>()?.text in softModifierKeywords && nextSibling is KtDeclaration) {
        // enum; class Foo
        return true
    }

    if (nextSibling is KtPrefixExpression && nextSibling.operationToken == KtTokens.EXCL) {
        val typeElement = semicolon.prevLeaf()?.getStrictParentOfType<KtTypeReference>()?.typeElement
        if (typeElement != null) {
            return typeElement !is KtNullableType // trailing '?' fixes parsing
        }
    }

    return false
}

private fun isRequiredForCompanion(semicolon: PsiElement): Boolean {
    val prev = semicolon.getPrevSiblingIgnoringWhitespaceAndComments() as? KtObjectDeclaration ?: return false
    if (!prev.isCompanion()) return false
    if (prev.nameIdentifier != null || prev.getChildOfType<KtClassBody>() != null) return false

    val next = semicolon.getNextSiblingIgnoringWhitespaceAndComments() ?: return false
    val firstChildNode = next.firstChild?.node ?: return false
    if (KtTokens.KEYWORDS.contains(firstChildNode.elementType)) return false

    return true
}

private val softModifierKeywords: List<String> = KtTokens.SOFT_KEYWORDS.types.mapNotNull { (it as? KtModifierKeywordToken)?.toString() }