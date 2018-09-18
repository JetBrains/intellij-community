// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.groovydoc.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.LookAheadLexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.TokenSet;

public class GroovyDocLexer extends LookAheadLexer {

  private static final TokenSet TOKENS_TO_MERGE = TokenSet.create(
    GroovyDocTokenTypes.mGDOC_COMMENT_DATA,
    GroovyDocTokenTypes.mGDOC_ASTERISKS,
    TokenType.WHITE_SPACE
  );

  public GroovyDocLexer() {
    super(new MergingLexerAdapter(new FlexAdapter(new _GroovyDocLexer(null)), TOKENS_TO_MERGE));
  }
}
