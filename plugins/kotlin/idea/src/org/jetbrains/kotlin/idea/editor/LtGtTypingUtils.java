// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.editor;

import com.intellij.codeInsight.editorActions.TypedHandlerUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.kotlin.lexer.KtToken;
import org.jetbrains.kotlin.lexer.KtTokens;

final class LtGtTypingUtils {
    private static final TokenSet INVALID_INSIDE_REFERENCE = TokenSet.create(KtTokens.SEMICOLON, KtTokens.LBRACE, KtTokens.RBRACE);

    private LtGtTypingUtils() {
    }

    static void handleKotlinAutoCloseLT(Editor editor) {
        TypedHandlerUtil.handleAfterGenericLT(editor, KtTokens.LT, KtTokens.GT, INVALID_INSIDE_REFERENCE);
    }

    static boolean handleKotlinGTInsert(Editor editor) {
        return TypedHandlerUtil.handleGenericGT(editor, KtTokens.LT, KtTokens.GT, INVALID_INSIDE_REFERENCE);
    }

    static void handleKotlinLTDeletion(Editor editor, int offset) {
        TypedHandlerUtil.handleGenericLTDeletion(editor, offset, KtTokens.LT, KtTokens.GT, INVALID_INSIDE_REFERENCE);
    }

    static boolean shouldAutoCloseAngleBracket(int offset, Editor editor) {
        return isAfterClassIdentifier(offset, editor) || isAfterToken(offset, editor, KtTokens.FUN_KEYWORD);
    }

    private static boolean isAfterClassIdentifier(int offset, Editor editor) {
        HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
        if (iterator.atEnd()) {
            return false;
        }

        if (iterator.getStart() > 0) {
            iterator.retreat();
        }

        return TypedHandlerUtil.isClassLikeIdentifier(offset, editor, iterator, KtTokens.IDENTIFIER);
    }

    static boolean isAfterToken(int offset, Editor editor, KtToken tokenType) {
        HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
        if (iterator.atEnd()) {
            return false;
        }

        if (iterator.getStart() > 0) {
            iterator.retreat();
        }

        if (iterator.getTokenType() == KtTokens.WHITE_SPACE && iterator.getStart() > 0) {
            iterator.retreat();
        }

        return iterator.getTokenType() == tokenType;
    }
}
