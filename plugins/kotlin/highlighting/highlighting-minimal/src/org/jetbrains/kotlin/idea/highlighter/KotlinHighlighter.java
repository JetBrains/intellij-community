// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens;
import org.jetbrains.kotlin.lexer.KtTokens;

import java.util.HashMap;
import java.util.Map;

public class KotlinHighlighter extends SyntaxHighlighterBase {
    private static final Map<IElementType, TextAttributesKey> keys1;
    private static final Map<IElementType, TextAttributesKey> keys2;

    @Override
    @NotNull
    public Lexer getHighlightingLexer() {
        return new KotlinHighlightingLexer();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        return pack(keys1.get(tokenType), keys2.get(tokenType));
    }

    static {
        keys1 = new HashMap<IElementType, TextAttributesKey>();
        keys2 = new HashMap<IElementType, TextAttributesKey>();

        fillMap(keys1, KtTokens.KEYWORDS, KotlinHighlightingColors.KEYWORD);

        keys1.put(KtTokens.VAL_KEYWORD, KotlinHighlightingColors.VAL_KEYWORD);
        keys1.put(KtTokens.VAR_KEYWORD, KotlinHighlightingColors.VAR_KEYWORD);
        keys1.put(KtTokens.AS_SAFE, KotlinHighlightingColors.KEYWORD);
        keys1.put(KtTokens.INTEGER_LITERAL, KotlinHighlightingColors.NUMBER);
        keys1.put(KtTokens.FLOAT_LITERAL, KotlinHighlightingColors.NUMBER);

        fillMap(
                keys1,
                TokenSet.andNot(KtTokens.OPERATIONS, TokenSet.orSet(TokenSet.create(KtTokens.IDENTIFIER, KtTokens.AT), KtTokens.KEYWORDS)),
                KotlinHighlightingColors.OPERATOR_SIGN
        );

        keys1.put(KtTokens.LPAR, KotlinHighlightingColors.PARENTHESIS);
        keys1.put(KtTokens.RPAR, KotlinHighlightingColors.PARENTHESIS);
        keys1.put(KtTokens.LBRACE, KotlinHighlightingColors.BRACES);
        keys1.put(KtTokens.RBRACE, KotlinHighlightingColors.BRACES);
        keys1.put(KtTokens.LBRACKET, KotlinHighlightingColors.BRACKETS);
        keys1.put(KtTokens.RBRACKET, KotlinHighlightingColors.BRACKETS);
        keys1.put(KtTokens.COMMA, KotlinHighlightingColors.COMMA);
        keys1.put(KtTokens.SEMICOLON, KotlinHighlightingColors.SEMICOLON);
        keys1.put(KtTokens.COLON, KotlinHighlightingColors.COLON);
        keys1.put(KtTokens.COLONCOLON, KotlinHighlightingColors.DOUBLE_COLON);
        keys1.put(KtTokens.QUEST, KotlinHighlightingColors.QUEST);
        keys1.put(KtTokens.DOT, KotlinHighlightingColors.DOT);
        keys1.put(KtTokens.ARROW, KotlinHighlightingColors.ARROW);

        keys1.put(KtTokens.OPEN_QUOTE, KotlinHighlightingColors.STRING);
        keys1.put(KtTokens.CLOSING_QUOTE, KotlinHighlightingColors.STRING);
        keys1.put(KtTokens.REGULAR_STRING_PART, KotlinHighlightingColors.STRING);
        keys1.put(KtTokens.LONG_TEMPLATE_ENTRY_END, KotlinHighlightingColors.STRING_ESCAPE);
        keys1.put(KtTokens.LONG_TEMPLATE_ENTRY_START, KotlinHighlightingColors.STRING_ESCAPE);
        keys1.put(KtTokens.SHORT_TEMPLATE_ENTRY_START, KotlinHighlightingColors.STRING_ESCAPE);

        keys1.put(KtTokens.ESCAPE_SEQUENCE, KotlinHighlightingColors.STRING_ESCAPE);

        keys1.put(KtTokens.CHARACTER_LITERAL, KotlinHighlightingColors.STRING);

        keys1.put(KtTokens.EOL_COMMENT, KotlinHighlightingColors.LINE_COMMENT);
        keys1.put(KtTokens.SHEBANG_COMMENT, KotlinHighlightingColors.LINE_COMMENT);
        keys1.put(KtTokens.BLOCK_COMMENT, KotlinHighlightingColors.BLOCK_COMMENT);
        keys1.put(KtTokens.DOC_COMMENT, KotlinHighlightingColors.DOC_COMMENT);

        fillMap(keys1, KDocTokens.KDOC_HIGHLIGHT_TOKENS, KotlinHighlightingColors.DOC_COMMENT);
        keys1.put(KDocTokens.TAG_NAME, KotlinHighlightingColors.DOC_COMMENT);
        keys2.put(KDocTokens.TAG_NAME, KotlinHighlightingColors.KDOC_TAG);

        keys1.put(TokenType.BAD_CHARACTER, KotlinHighlightingColors.BAD_CHARACTER);
    }
}
