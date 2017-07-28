package ru.adelf.idea.dotenv.extension;

import com.intellij.codeInsight.generation.CommenterDataHolder;
import com.intellij.codeInsight.generation.SelfManagingCommenter;
import com.intellij.lang.Commenter;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DotEnvCommenter implements Commenter, SelfManagingCommenter<CommenterDataHolder> {
    private static final String HASH_COMMENT_PREFIX = "#";
    private static final String SLASH_COMMENT_PREFIX = "//";

    public String getLineCommentPrefix() {
        return HASH_COMMENT_PREFIX;
    }

    public String getBlockCommentPrefix() {
        return null;
    }

    public String getBlockCommentSuffix() {
        return null;
    }

    public String getCommentedBlockCommentPrefix() {
        return null;
    }

    public String getCommentedBlockCommentSuffix() {
        return null;
    }

    @Nullable
    @Override
    public CommenterDataHolder createLineCommentingState(int startLine, int endLine, @NotNull Document document, @NotNull PsiFile file) {
        return null;
    }

    @Nullable
    @Override
    public CommenterDataHolder createBlockCommentingState(int selectionStart,
                                                          int selectionEnd,
                                                          @NotNull Document document,
                                                          @NotNull PsiFile file) {
        return null;
    }

    @Override
    public void commentLine(int line, int offset, @NotNull Document document, @NotNull CommenterDataHolder data) {
        document.insertString(offset, HASH_COMMENT_PREFIX);
    }

    @Override
    public void uncommentLine(int line, int offset, @NotNull Document document, @NotNull CommenterDataHolder data) {
        if(document.getText().charAt(offset) == '#') {
            document.deleteString(offset, offset + HASH_COMMENT_PREFIX.length());
        } else {
            document.deleteString(offset, offset + SLASH_COMMENT_PREFIX.length());
        }
    }

    @Override
    public boolean isLineCommented(int line, int offset, @NotNull Document document, @NotNull CommenterDataHolder data) {
        return CharArrayUtil.regionMatches(document.getCharsSequence(), offset, HASH_COMMENT_PREFIX) ||
                CharArrayUtil.regionMatches(document.getCharsSequence(), offset, SLASH_COMMENT_PREFIX);
    }

    @Nullable
    @Override
    public String getCommentPrefix(int line, @NotNull Document document, @NotNull CommenterDataHolder data) {
        return HASH_COMMENT_PREFIX;
    }

    @Nullable
    @Override
    public TextRange getBlockCommentRange(int selectionStart,
                                          int selectionEnd,
                                          @NotNull Document document,
                                          @NotNull CommenterDataHolder data) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public String getBlockCommentPrefix(int selectionStart, @NotNull Document document, @NotNull CommenterDataHolder data) {
        return getBlockCommentPrefix();
    }

    @Nullable
    @Override
    public String getBlockCommentSuffix(int selectionEnd, @NotNull Document document, @NotNull CommenterDataHolder data) {
        return getBlockCommentSuffix();
    }

    @Override
    public void uncommentBlockComment(int startOffset, int endOffset, Document document, CommenterDataHolder data) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public TextRange insertBlockComment(int startOffset, int endOffset, Document document, CommenterDataHolder data) {
        throw new UnsupportedOperationException();
    }
}