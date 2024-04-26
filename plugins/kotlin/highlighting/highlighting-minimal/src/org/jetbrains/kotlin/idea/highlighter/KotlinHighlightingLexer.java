// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.kotlin.kdoc.lexer.KDocLexer;
import org.jetbrains.kotlin.lexer.KotlinLexer;
import org.jetbrains.kotlin.lexer.KtTokens;

public class KotlinHighlightingLexer extends LayeredLexer {
    public KotlinHighlightingLexer() {
        super(new KotlinLexer());

        registerSelfStoppingLayer(new KDocLexer(), new IElementType[]{KtTokens.DOC_COMMENT}, IElementType.EMPTY_ARRAY);
        registerSelfStoppingLayer(new StringLiteralLexer('\'', KtTokens.CHARACTER_LITERAL),
                                  new IElementType[]{KtTokens.CHARACTER_LITERAL}, IElementType.EMPTY_ARRAY);


    }
}
