// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.editorActions.enter.EnterBetweenBracesNoCommitDelegate
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.lexer.KtTokens

class KotlinEnterBetweenBracesAndBracketsNoCommitDelegate : EnterBetweenBracesNoCommitDelegate() {
    override fun isCommentType(type: IElementType?): Boolean = type in KtTokens.COMMENTS

    override fun isBracePair(lBrace: Char, rBrace: Char): Boolean = super.isBracePair(lBrace, rBrace) || (lBrace == '[' && rBrace == ']')
}
