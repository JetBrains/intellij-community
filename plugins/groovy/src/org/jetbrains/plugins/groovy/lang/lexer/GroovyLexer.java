package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.TokenSet;

/**
 * @author Ilya Sergey
 */
public class GroovyLexer extends MergingLexerAdapter {

  public GroovyLexer() {
    super(new GroovyFlexLexer(),
      TokenSet.create(
        GroovyTokenTypes.mSH_COMMENT,
        GroovyTokenTypes.mSL_COMMENT,
        GroovyTokenTypes.mML_COMMENT,
        GroovyTokenTypes.mWS,
        GroovyTokenTypes.mWRONG_GSTRING_LITERAL
      ));
  }

}
