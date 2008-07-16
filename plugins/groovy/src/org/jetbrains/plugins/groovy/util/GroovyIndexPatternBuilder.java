package org.jetbrains.plugins.groovy.util;

import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;
import com.intellij.lexer.Lexer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * User: Dmitry.Krasilschikov
 * Date: 16.07.2008
 */
public class GroovyIndexPatternBuilder implements IndexPatternBuilder {
    public Lexer getIndexingLexer(PsiFile file) {
        if (file instanceof GroovyFile) {
            return new GroovyLexer();
        }
        return null;
    }

    public TokenSet getCommentTokenSet(PsiFile file) {
        return GroovyTokenTypes.COMMENT_SET;
    }

    public int getCommentStartDelta(IElementType tokenType) {
        return 0;
    }

    public int getCommentEndDelta(IElementType tokenType) {
        return 0;
    }
}
