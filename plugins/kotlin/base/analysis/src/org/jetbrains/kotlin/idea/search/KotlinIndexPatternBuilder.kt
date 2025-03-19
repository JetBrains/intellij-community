// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search

import com.intellij.lexer.Lexer
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile

class KotlinIndexPatternBuilder : IndexPatternBuilderAdapter() {
    override fun getCommentTokenSet(file: PsiFile): TokenSet? {
        return if (file is KtFile) TODO_COMMENT_TOKENS else null
    }

    override fun getIndexingLexer(file: PsiFile): Lexer? {
        return if (file is KtFile) KotlinLexer() else null
    }

    override fun getCommentStartDelta(tokenType: IElementType?): Int = 0

    override fun getCommentEndDelta(tokenType: IElementType?): Int = when (tokenType) {
        KtTokens.BLOCK_COMMENT -> "*/".length
        else -> 0
    }
}

private val TODO_COMMENT_TOKENS: TokenSet = TokenSet.orSet(KtTokens.COMMENTS, TokenSet.create(KDocTokens.KDOC))
