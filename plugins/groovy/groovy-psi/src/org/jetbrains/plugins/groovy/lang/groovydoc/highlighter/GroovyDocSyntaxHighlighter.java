// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.groovydoc.highlighter;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocLexer;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;

import java.util.HashMap;
import java.util.Map;

public class GroovyDocSyntaxHighlighter extends SyntaxHighlighterBase {
  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<>();
  private static final IElementType mGDOC_COMMENT_CONTENT = new GroovyDocElementType("GDOC_COMMENT_CONTENT");

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new GroovyDocLexer();
  }

  private static final TokenSet tGDOC_COMMENT_TAGS = TokenSet.create(
      GroovyDocTokenTypes.mGDOC_TAG_NAME
  );

  private static final TokenSet tGDOC_COMMENT_CONTENT = TokenSet.create(
      mGDOC_COMMENT_CONTENT
  );


  static {
    fillMap(ATTRIBUTES, tGDOC_COMMENT_CONTENT, GroovySyntaxHighlighter.DOC_COMMENT_CONTENT);
    fillMap(ATTRIBUTES, tGDOC_COMMENT_TAGS, GroovySyntaxHighlighter.DOC_COMMENT_TAG);
  }


  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType type) {
    return pack(ATTRIBUTES.get(type));
  }
}
