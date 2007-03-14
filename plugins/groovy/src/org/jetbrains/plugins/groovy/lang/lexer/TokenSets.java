package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.psi.tree.TokenSet;

/**
 * Utility class, tha contains various useful TokenSets
 *
 * @author Ilya Sergey
 */
public abstract class TokenSets implements GroovyTokenTypes {

  public static TokenSet COMMENTS_TOKEN_SET = TokenSet.create(
          mSL_COMMENT,
          mML_COMMENT
  );

  public static TokenSet WHITE_SPACE_TOKEN_SET = TokenSet.create(
          mWS
  );

}
