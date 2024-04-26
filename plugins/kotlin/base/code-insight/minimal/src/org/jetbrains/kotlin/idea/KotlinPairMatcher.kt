// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.codeInsight.highlighting.PairedBraceAndAnglesMatcher
import com.intellij.codeInsight.hint.DeclarationRangeUtil
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassBody

class KotlinPairedBraceMatcher :
    PairedBraceAndAnglesMatcher(
        KotlinPairMatcher(),
        KotlinLanguage.INSTANCE,
        KotlinFileType.INSTANCE,
        TYPE_TOKENS_INSIDE_ANGLE_BRACKETS
    ) {
    override fun lt(): IElementType = KtTokens.LT
    override fun gt(): IElementType = KtTokens.GT
}

private val TYPE_TOKENS_INSIDE_ANGLE_BRACKETS = TokenSet.orSet(
    KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET,
    TokenSet.create(
        KtTokens.IDENTIFIER, KtTokens.COMMA,
        KtTokens.AT,
        KtTokens.RBRACKET, KtTokens.LBRACKET,
        KtTokens.IN_KEYWORD, KtTokens.OUT_KEYWORD, KtTokens.MUL,
        KtTokens.COLON, KtTokens.COLONCOLON, KtTokens.LPAR, KtTokens.RPAR,
        KtTokens.CLASS_KEYWORD, KtTokens.DOT
    )
)

class KotlinPairMatcher : PairedBraceMatcher {
    private val pairs = arrayOf(
        BracePair(KtTokens.LPAR, KtTokens.RPAR, false),
        BracePair(KtTokens.LONG_TEMPLATE_ENTRY_START, KtTokens.LONG_TEMPLATE_ENTRY_END, false),
        BracePair(KtTokens.LBRACE, KtTokens.RBRACE, true),
        BracePair(KtTokens.LBRACKET, KtTokens.RBRACKET, false),
        BracePair(KtTokens.LT, KtTokens.GT, false),
    )

    override fun getPairs(): Array<BracePair> = pairs

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean {
        return if (lbraceType == KtTokens.LONG_TEMPLATE_ENTRY_START) {
            // KotlinTypedHandler insert paired brace in this case
            false
        } else KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(contextType)
                || contextType === KtTokens.COLON
                || contextType === KtTokens.SEMICOLON
                || contextType === KtTokens.COMMA
                || contextType === KtTokens.RPAR
                || contextType === KtTokens.RBRACKET
                || contextType === KtTokens.RBRACE
                || contextType === KtTokens.LBRACE
                || contextType === KtTokens.LONG_TEMPLATE_ENTRY_END

    }

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int {
        val element = file.findElementAt(openingBraceOffset)
        if (element == null || element is PsiFile) return openingBraceOffset
        val parent = element.parent
        return when  {
            parent is KtClassBody || parent.elementType == KtNodeTypes.BLOCK ->
                DeclarationRangeUtil.getPossibleDeclarationAtRange(parent.parent)?.startOffset ?: openingBraceOffset

            else -> openingBraceOffset
        }
    }
}
