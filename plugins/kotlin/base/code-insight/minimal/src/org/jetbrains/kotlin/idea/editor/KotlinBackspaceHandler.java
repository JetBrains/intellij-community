// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor;

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.lexer.KtTokens;

import static org.jetbrains.kotlin.idea.editor.LtGtTypingUtils.isAfterToken;

public class KotlinBackspaceHandler extends BackspaceHandlerDelegate {
    private boolean deleteGt;

    @Override
    public void beforeCharDeleted(char c, PsiFile file, Editor editor) {
        int offset = editor.getCaretModel().getOffset() - 1;
        deleteGt = c == '<' && file.getFileType() == KotlinFileType.INSTANCE &&
                   (isAfterToken(offset, editor, KtTokens.FUN_KEYWORD) ||
                    isAfterToken(offset, editor, KtTokens.IDENTIFIER));
    }

    @Override
    public boolean charDeleted(char c, PsiFile file, Editor editor) {
        int offset = editor.getCaretModel().getOffset();
        CharSequence chars = editor.getDocument().getCharsSequence();
        if (editor.getDocument().getTextLength() <= offset) return false; //virtual space after end of file

        char c1 = chars.charAt(offset);
        if (c == '<' && deleteGt) {
            if (c1 == '>') {
                LtGtTypingUtils.handleKotlinLTDeletion(editor, offset);
            }
            return true;
        }

        return false;
    }
}
